package com.terracottatech.qa.angela.agent.kit;

import com.terracottatech.qa.angela.common.TerracottaServerInstance;
import com.terracottatech.qa.angela.common.distribution.DistributionController;
import com.terracottatech.qa.angela.common.tcconfig.ServerSymbolicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.terracottatech.qa.angela.common.tcconfig.TcConfig;
import com.terracottatech.qa.angela.common.tcconfig.TerracottaServer;
import com.terracottatech.qa.angela.common.topology.Topology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Installation instance of a Terracotta server
 */
public class TerracottaInstall {

  private static final Logger logger = LoggerFactory.getLogger(TerracottaInstall.class);

  private final Topology topology;
  private final File installLocation;
  //  private final NetworkController networkController;
  private final Map<ServerSymbolicName, TerracottaServerInstance> terracottaServerInstances;


  public TerracottaInstall(final File location, final Topology topology) {
    this.topology = topology;
    this.terracottaServerInstances = createTerracottaServerInstancesMap(topology.getTcConfigs(), topology.createDistributionController(), location);
    this.installLocation = location;
//    this.networkController = networkController;
  }

  private static Map<ServerSymbolicName, TerracottaServerInstance> createTerracottaServerInstancesMap(
      final TcConfig[] tcConfigs, final DistributionController distributionController, final File location) {
    Map<ServerSymbolicName, TerracottaServerInstance> terracottaServerInstances = new HashMap<>();
    for (TcConfig tcConfig : tcConfigs) {
      Map<ServerSymbolicName, TerracottaServer> servers = tcConfig.getServers();
      for (TerracottaServer terracottaServer : servers.values()) {
        terracottaServerInstances.put(terracottaServer.getServerSymbolicName(), new TerracottaServerInstance(terracottaServer.getServerSymbolicName(), distributionController, location));
      }
    }
    return terracottaServerInstances;
  }

  public TerracottaServerInstance getTerracottaServerInstance(final TerracottaServer terracottaServer) {
    return terracottaServerInstances.get(terracottaServer.getServerSymbolicName());
  }

  public File getInstallLocation() {
    return installLocation;
  }
}