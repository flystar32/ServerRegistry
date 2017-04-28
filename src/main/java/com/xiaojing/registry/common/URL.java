package com.xiaojing.registry.common;

import com.google.common.base.Splitter;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by xiaojing on 16/6/20.
 */
public class URL {

  private static final Logger LOGGER = LoggerFactory.getLogger(URL.class);


  public static String PATH_SEPARATOR = "/";
  public static String IP_PORT_SEPARATOR = ":";

  private String serviceName;
  private String host;
  private int port;
  private Map<String, String> extraData;


  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }


  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public Map<String, String> getExtraData() {
    return extraData;
  }

  public void setExtraData(Map<String, String> extraData) {
    this.extraData = extraData;
  }

  public URL() {

  }

  @Deprecated
  public URL(String serviceName, String host, int port, Map<String, String> extraData) {
    this.serviceName = serviceName;
    this.host = host;
    this.port = port;
    this.extraData = extraData;
  }

  public URL(String serviceName, String host, int port) {
    this.serviceName = serviceName;
    this.host = host;
    this.port = port;
  }

  public URL(String serviceName) {
    this(serviceName, null, 0);
  }


  public String getUrlPath() {

    StringBuilder sb = new StringBuilder();

    if (StringUtils.isBlank(serviceName)) {
      return sb.append(PATH_SEPARATOR).toString();
    }

    sb.append(PATH_SEPARATOR).append(serviceName);

    if (StringUtils.isBlank(host) || port == 0) {
      return sb.toString();
    }

    sb.append(PATH_SEPARATOR).append(host).append(IP_PORT_SEPARATOR).append(port);
    return sb.toString();

  }


  /**
   * todo tobe complete
   * 解析时按顺序来,不方便扩展
   * 目前的路径设定为/servicename/host:port
   * 解析到格式不正确的路径,会返回null,修改成直接变成抛出Exception
   * 解析到不正确的host,会返回null
   */
  public static URL parseUrlPath(String path) throws Exception {
    String originPath = path;
    if (null == path || StringUtils.isBlank(path)) {
      throw new RuntimeException();
    }
    String serviceName;
    String host;
    int port;

    int index = path.indexOf(PATH_SEPARATOR);
    if (index != 0) {
      LOGGER.error("path format wrong,path={}", originPath);
      throw new Exception("wrong path format");
    }

    path = path.substring(index + 1);
    index = path.indexOf(PATH_SEPARATOR);
    if (index == 0) {
      LOGGER.error("path format wrong,path={}", originPath);
      throw new Exception("wrong path format");
    }
    if (index < 0) {
      if (StringUtils.isBlank(path)) {
        return new URL();
      }
      return new URL(path);
    }

    serviceName = path.substring(0, index);
    path = path.substring(index + 1);
    index = path.indexOf(PATH_SEPARATOR);
    if (index == 0) {
      LOGGER.error("path format wrong,path={}", originPath);
      throw new Exception("wrong path format");
    }
    try {
      host = StringUtils.substringBefore(path, IP_PORT_SEPARATOR);
      port = Integer.parseInt(StringUtils.substringAfter(path, IP_PORT_SEPARATOR));
    } catch (NumberFormatException nfe) {
      LOGGER.error("wrong number format,path={},e=", originPath, nfe);
      throw new Exception("wrong number format");
    }

    if (!NetUtils.isValidHost(host)) {
      LOGGER.error("wrong information,url has invalid host,host={}", host);
      throw new Exception("invalid host");
    }

    return new URL(serviceName, host, port);
  }

  @Deprecated
  public static URL parseUrlPath(String path, String extraDataStr) throws Exception {
    URL url = parseUrlPath(path);
    try {
      /**node节点的其他信息,使用&分割开,=分割key和value*/
      Map<String, String>
          extraData =
          Splitter.on("&").withKeyValueSeparator("=").split(extraDataStr);
      url.setExtraData(extraData);
    } catch (Exception e) {
      LOGGER.warn("parase key value pair error,e=", e);
    }
    return url;
  }


  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder("[");
    sb.append("serviceName:");
    if (this.serviceName == null) {
      sb.append("null");
    } else {
      sb.append(this.serviceName);
    }
    sb.append(",");

    sb.append("host:");
    if (this.host == null) {
      sb.append("null");
    } else {
      sb.append(this.host);
    }
    sb.append(",");

    sb.append("port:");
    sb.append(this.port);

    sb.append("]");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    int prime1 = 31;
    int prime2 = 17;
    int hash = 1;
    hash = hash + prime1 * (this.serviceName != null ? this.serviceName.hashCode() : 0);
    hash = hash + prime2 * this.port;
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof URL) {
      URL other = (URL) object;
      return (StringUtils.equals(serviceName, other.serviceName)
              && StringUtils.equals(host, other.host)
              && getPort() == other.getPort());
    }
    return false;
  }
}
