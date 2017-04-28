package com.xiaojing.registry;

import com.xiaojing.registry.common.URL;

import java.util.List;

/**
 * Created by xiaojing on 16/6/20.
 */
public interface DiscoveryService {

  void subscribe(URL url, NotifyListener listener);

  List<URL> pull(URL url) throws Exception;

}
