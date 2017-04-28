package com.xiaojing.registry;

import com.xiaojing.registry.common.URL;

/**
 * Created by xiaojing on 16/6/20.
 */
public interface RegistryService {

  void register(URL url);

  void registerWatcher(URL url, NotifyListener listener);

  void checkNodeExist(URL url);

}
