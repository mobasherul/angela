package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.agent.Agent;
import com.terracottatech.qa.angela.client.remote.agent.NoRemoteAgentLauncher;
import com.terracottatech.qa.angela.client.remote.agent.RemoteAgentLauncher;
import com.terracottatech.qa.angela.common.TerracottaCommandLineEnvironment;
import com.terracottatech.qa.angela.common.client.Barrier;
import com.terracottatech.qa.angela.common.distribution.Distribution;
import com.terracottatech.qa.angela.common.tcconfig.License;
import com.terracottatech.qa.angela.common.tms.security.config.TmsServerSecurityConfig;
import com.terracottatech.qa.angela.common.topology.InstanceId;
import com.terracottatech.qa.angela.common.topology.Topology;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.logger.NullLogger;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClusterFactory implements AutoCloseable {
  static final boolean SKIP_UNINSTALL = Boolean.getBoolean("tc.qa.angela.skipUninstall")
      || Boolean.getBoolean("skipUninstall"); // legacy system prop name

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterFactory.class);
  private static final Set<String> DEFAULT_ALLOWED_JDK_VENDORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("Oracle Corporation", "sun", "openjdk")));
  private static final String DEFAULT_JDK_VERSION = "1.8";

  private static final String TSA = "tsa";
  private static final String TMS = "tms";
  private static final String CLIENT = "client";
  private static final DateTimeFormatter PATH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-hhmmss");


  private transient final RemoteAgentLauncher remoteAgentLauncher;
  private final List<AutoCloseable> controllers = new ArrayList<>();
  private final String idPrefix;
  private final AtomicInteger instanceIndex;
  private final TerracottaCommandLineEnvironment tcEnv;
  private final Map<String, InstanceId> nodeToInstanceId = new HashMap<>();
  private Ignite ignite;
  private boolean localhostOnly;
  private Agent.Node localhostAgent;

  public ClusterFactory(String idPrefix) {
    this(idPrefix, new NoRemoteAgentLauncher(), new TerracottaCommandLineEnvironment(DEFAULT_JDK_VERSION, DEFAULT_ALLOWED_JDK_VENDORS, null));
  }

  public ClusterFactory(String idPrefix, RemoteAgentLauncher remoteAgentLauncher) {
    this(idPrefix, remoteAgentLauncher, new TerracottaCommandLineEnvironment(DEFAULT_JDK_VERSION, DEFAULT_ALLOWED_JDK_VENDORS, null));
  }

  public ClusterFactory(String idPrefix, TerracottaCommandLineEnvironment tcEnv) {
    this(idPrefix, new NoRemoteAgentLauncher(), tcEnv);
  }

  public ClusterFactory(String idPrefix, RemoteAgentLauncher remoteAgentLauncher, TerracottaCommandLineEnvironment tcEnv) {
    // Using UTC to have consistent layout even in case of timezone skew between client and server.
    this.idPrefix = idPrefix + "-" + LocalDateTime.now(ZoneId.of("UTC")).format(PATH_FORMAT);
    this.remoteAgentLauncher = remoteAgentLauncher;
    this.tcEnv = tcEnv;
    this.instanceIndex = new AtomicInteger();
  }

  private InstanceId init(String type, Collection<String> targetServerNames) {
    if (targetServerNames.isEmpty()) {
      throw new IllegalArgumentException("Cannot initialize with 0 server");
    }

    InstanceId instanceId = new InstanceId(idPrefix + "-" + instanceIndex.getAndIncrement(), type);
    for (String targetServerName : targetServerNames) {
      if (targetServerName == null) {
        throw new IllegalArgumentException("Cannot initialize with a null server name");
      }
      if (!targetServerName.equals("localhost")) {
        remoteAgentLauncher.remoteStartAgentOn(targetServerName);
      }
      nodeToInstanceId.put(targetServerName, instanceId);
    }

    if (ignite != null) {
      return instanceId;
    }

    if (isLocalhostOnly(targetServerNames)) {
      LOGGER.info("spawning localhost agent");
      localhostAgent = new Agent.Node("localhost");
      localhostAgent.init();
      localhostOnly = true;
    }

    TcpDiscoverySpi spi = new TcpDiscoverySpi();
    TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
    if (localhostOnly) {
      ipFinder.setAddresses(targetServerNames.stream().map(targetServerName -> targetServerName + ":40000").collect(Collectors.toList()));
    } else {
      ipFinder.setAddresses(targetServerNames);
    }
    spi.setJoinTimeout(10000);
    spi.setIpFinder(ipFinder);

    IgniteConfiguration cfg = new IgniteConfiguration();
    cfg.setDiscoverySpi(spi);
    cfg.setClientMode(true);
    cfg.setPeerClassLoadingEnabled(true);
    boolean enableLogging = Boolean.getBoolean(Agent.IGNITE_LOGGING_SYSPROP_NAME);
    cfg.setGridLogger(enableLogging ? new Slf4jLogger() : new NullLogger());
    cfg.setIgniteInstanceName("Instance@" + instanceId);
    cfg.setMetricsLogFrequency(0);

    try {
      this.ignite = Ignition.start(cfg);
    } catch (IgniteException e) {
      throw new RuntimeException("Cannot start angela; error connecting to agents : " + targetServerNames, e);
    }

    return instanceId;
  }

  private static boolean isLocalhostOnly(Collection<String> targetServerNames) {
    for (String targetServerName : targetServerNames) {
      if (!targetServerName.equals("localhost")) {
        return false;
      }
    }
    return true;
  }

  public Barrier barrier(String name, int count) {
    if (ignite == null) {
      throw new IllegalStateException("No cluster component started; cannot create a barrier");
    }
    return new Barrier(ignite, count, name);
  }

  public Tsa tsa(Topology topology) {
    InstanceId instanceId = init(TSA, topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology, null, tcEnv);
    controllers.add(tsa);
    return tsa;
  }

  public Tsa tsa(Topology topology, License license) {
    InstanceId instanceId = init(TSA, topology.getServersHostnames());

    Tsa tsa = new Tsa(ignite, instanceId, topology, license, tcEnv);
    controllers.add(tsa);
    return tsa;
  }

  public Client client(String nodeName) {
    return client(nodeName, this.tcEnv);
  }

  public Client client(String nodeName, TerracottaCommandLineEnvironment tcEnv) {
    if (!"localhost".equals(nodeName) && localhostOnly) {
      throw new IllegalArgumentException("localhost agent started, connection to remote agent '" + nodeName + "' is not possible");
    }

    InstanceId instanceId = init(CLIENT, Collections.singleton(nodeName));

    Client client = new Client(ignite, instanceId, nodeName, localhostOnly, tcEnv);
    controllers.add(client);
    return client;
  }

  public Tms tms(Distribution distribution, License license, String hostname) {
    return tms(distribution, license, hostname, null);
  }

  public Tms tms(Distribution distribution, License license, String hostname, TmsServerSecurityConfig securityConfig) {
    InstanceId instanceId = init(TMS, Collections.singletonList(hostname));

    Tms tms = new Tms(ignite, instanceId, license, hostname, distribution, securityConfig, tcEnv);
    controllers.add(tms);
    return tms;
  }

  @Override
  public void close() {
    List<Exception> exceptions = new ArrayList<>();

    for (AutoCloseable controller : controllers) {
      try {
        controller.close();
      } catch (Exception e) {
        exceptions.add(e);
      }
    }
    controllers.clear();

    for (String nodeName : nodeToInstanceId.keySet()) {
      try {
        IgniteHelper.checkAgentHealth(ignite, nodeName);
        ClusterGroup location = ignite.cluster().forAttribute("nodename", nodeName);
        InstanceId instanceId = nodeToInstanceId.get(nodeName);
        ignite.compute(location).broadcast((IgniteRunnable) () -> Agent.CONTROLLER.cleanup(instanceId));
      } catch (Exception e) {
        exceptions.add(e);
      }
    }
    nodeToInstanceId.clear();

    if (ignite != null) {
      try {
        ignite.close();
      } catch (Exception e) {
        exceptions.add(e);
      }
      ignite = null;
    }

    try {
      remoteAgentLauncher.close();
    } catch (Exception e) {
      exceptions.add(e);
    }

    if (localhostAgent != null) {
      try {
        LOGGER.info("shutting down localhost agent");
        localhostAgent.shutdown();
      } catch (Exception e) {
        exceptions.add(e);
      }
      localhostAgent = null;
    }

    if (!exceptions.isEmpty()) {
      RuntimeException runtimeException = new RuntimeException("Error while closing down Cluster Factory prefixed with " + idPrefix);
      exceptions.forEach(runtimeException::addSuppressed);
      throw runtimeException;
    }
  }

}
