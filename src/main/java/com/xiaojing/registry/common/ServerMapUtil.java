package com.xiaojing.registry.common;

import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

/**
 * Created by xiaojing on 16/6/15.
 */
public class ServerMapUtil {


  /**
   * key为servicename
   */
  public static Map<String, URLGroup> serverMap = Maps.newConcurrentMap();

  public static void syncServerMap(List<URL> urls) {

    if (null == urls || urls.size() == 0) {
      return;/**如果传入的urls为空,则不进行同步*/
    }

    Map<String, URLGroup> currentMap = Maps.newConcurrentMap();

    for (URL url : urls) {
      URLGroup urlGroup = currentMap.get(url.getServiceName());
      if (urlGroup == null) {
        urlGroup = new URLGroup();
        currentMap.put(url.getServiceName(), urlGroup);
      }
      urlGroup.addUrl(url);
    }

    serverMap = currentMap;


  }

}
