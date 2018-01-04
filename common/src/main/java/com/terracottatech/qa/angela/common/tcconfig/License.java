package com.terracottatech.qa.angela.common.tcconfig;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Files;

/**
 * Created by esebasti on 7/21/17.
 */
public class License implements Serializable {

  private final String licenseContent;

  public License(URL licensePath) {
    try (InputStream is = licensePath.openStream()) {
      if (is == null) {
        throw new IllegalArgumentException("License file is not present");
      }
      licenseContent = IOUtils.toString(is);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }


  public void WriteToFile(File file) {
    try  {
      System.out.println("->"+file.toPath().toAbsolutePath());
      Files.write(file.toPath().toAbsolutePath(), licenseContent.getBytes());
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
}
