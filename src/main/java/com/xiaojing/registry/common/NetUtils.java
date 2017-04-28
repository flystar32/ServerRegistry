package com.xiaojing.registry.common;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Created by xiaojing on 16/6/28.
 */
public class NetUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(NetUtils.class);

  public static final String LOCALHOST = "127.0.0.1";

  public static final String ANYHOST = "0.0.0.0";

  private static volatile InetAddress LOCAL_ADDRESS = null;

  private static final Pattern LOCAL_IP_PATTERN = Pattern.compile("127(\\.\\d{1,3}){3}$");

  private static final Pattern ADDRESS_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}\\:\\d{1,5}$");

  private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3,5}$");


  public static boolean isInvalidLocalHost(String host) {
    return host == null || host.length() == 0 || host.equalsIgnoreCase("localhost") || host
        .equals("0.0.0.0")
           || (LOCAL_IP_PATTERN.matcher(host).matches());
  }


  public static boolean isValidAddress(InetAddress address) {
    if (address == null || address.isLoopbackAddress()) {
      return false;
    }
    String name = address.getHostAddress();
    return (name != null
            && !ANYHOST.equals(name)
            && !LOCALHOST.equals(name)
            && IP_PATTERN.matcher(name).matches());
  }

  /**
   * 有效的主机地址,非本地地址
   */
  public static boolean isValidHost(String host) {
    return (host != null
            && !ANYHOST.equals(host)
            && !LOCALHOST.equals(host)
            && IP_PATTERN.matcher(host).matches());
  }

  public static InetAddress getLocalAddress() {
    if (LOCAL_ADDRESS != null) {
      return LOCAL_ADDRESS;
    }
    InetAddress localAddress = getLocalAddress0();
    LOCAL_ADDRESS = localAddress;
    return localAddress;
  }

  public static InetAddress getLocalAddress0() {
    InetAddress localAddress = null;
    try {
      localAddress = InetAddress.getLocalHost();
      if (isValidAddress(localAddress)) {
        return localAddress;
      }
    } catch (Throwable e) {
      LOGGER.warn("Failed to retriving ip address, " + e.getMessage(), e);
    }
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      if (interfaces != null) {
        while (interfaces.hasMoreElements()) {
          try {
            NetworkInterface network = interfaces.nextElement();
            if (network.isVirtual()) {
              continue;/**如果是虚拟网卡,排除此网卡*/
            }
            Enumeration<InetAddress> addresses = network.getInetAddresses();
            if (addresses != null) {
              while (addresses.hasMoreElements()) {
                try {
                  InetAddress address = addresses.nextElement();
                  if (isValidAddress(address)) {
                    return address;
                  }
                } catch (Throwable e) {
                  LOGGER.warn("Failed to retriving ip address, " + e.getMessage(), e);
                }
              }
            }
          } catch (Throwable e) {
            LOGGER.warn("Failed to retriving ip address, " + e.getMessage(), e);
          }
        }
      }
    } catch (Throwable e) {
      LOGGER.warn("Failed to retriving ip address, " + e.getMessage(), e);
    }
    LOGGER.error("Could not get local host ip address, will use 127.0.0.1 instead.");
    return localAddress;
  }


  public static InetAddress getLocalAddressBySocket(String host, int port) {
    if (StringUtils.isBlank(host) || port == 0) {
      return null;
    }

    try {
      Socket socket = new Socket();
      try {
        SocketAddress addr = new InetSocketAddress(host, port);
        socket.connect(addr, 1000);
        return socket.getLocalAddress();
      } finally {
        try {
          socket.close();
        } catch (Throwable e) {
        }
      }
    } catch (Exception e) {
      LOGGER.warn(String.format(
          "Failed to retrive local address by connecting to dest host:port(%s:%s) false, e=%s",
          host, port, e));
    }
    return null;
  }

}
