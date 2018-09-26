package com.terracottatech.qa.angela.client.config.custom;

import com.terracottatech.qa.angela.client.config.ClientArrayConfigurationContext;
import com.terracottatech.qa.angela.client.config.ConfigurationContext;
import com.terracottatech.qa.angela.client.config.RemotingConfigurationContext;
import com.terracottatech.qa.angela.client.config.TmsConfigurationContext;
import com.terracottatech.qa.angela.client.config.TsaConfigurationContext;
import com.terracottatech.qa.angela.client.remote.agent.SshRemoteAgentLauncher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class CustomConfigurationContext implements ConfigurationContext {

  public static final Set<String> DEFAULT_ALLOWED_JDK_VENDORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("zulu", "Oracle Corporation", "sun", "openjdk")));
  public static final String DEFAULT_JDK_VERSION = "1.8";

  private CustomRemotingConfigurationContext customRemotingConfigurationContext = new CustomRemotingConfigurationContext().remoteAgentLauncherSupplier(SshRemoteAgentLauncher::new);
  private CustomTsaConfigurationContext customTsaConfigurationContext;
  private CustomTmsConfigurationContext customTmsConfigurationContext;
  private CustomClientArrayConfigurationContext customClientArrayConfigurationContext;

  public static CustomConfigurationContext customConfigurationContext() {
    return new CustomConfigurationContext();
  }

  protected CustomConfigurationContext() {
  }

  @Override
  public RemotingConfigurationContext remoting() {
    return customRemotingConfigurationContext;
  }

  public CustomConfigurationContext remoting(CustomRemotingConfigurationContext customRemotingConfigurationContext) {
    this.customRemotingConfigurationContext = customRemotingConfigurationContext;
    return this;
  }

  @Override
  public TsaConfigurationContext tsa() {
    return customTsaConfigurationContext;
  }

  public CustomConfigurationContext tsa(Consumer<CustomTsaConfigurationContext> tsa) {
    if (customTsaConfigurationContext != null) {
      throw new IllegalStateException("TSA config already defined");
    }
    customTsaConfigurationContext = new CustomTsaConfigurationContext();
    tsa.accept(customTsaConfigurationContext);
    return this;
  }

  @Override
  public TmsConfigurationContext tms() {
    return customTmsConfigurationContext;
  }

  public CustomConfigurationContext tms(Consumer<CustomTmsConfigurationContext> tms) {
    if (customTmsConfigurationContext != null) {
      throw new IllegalStateException("TMS config already defined");
    }
    customTmsConfigurationContext = new CustomTmsConfigurationContext();
    tms.accept(customTmsConfigurationContext);
    return this;
  }

  @Override
  public ClientArrayConfigurationContext clientArray() {
    return customClientArrayConfigurationContext;
  }

  public CustomConfigurationContext clientArray(Consumer<CustomClientArrayConfigurationContext> clientArray) {
    if (customClientArrayConfigurationContext != null) {
      throw new IllegalStateException("client array config already defined");
    }
    customClientArrayConfigurationContext = new CustomClientArrayConfigurationContext();
    clientArray.accept(customClientArrayConfigurationContext);
    return this;
  }

}