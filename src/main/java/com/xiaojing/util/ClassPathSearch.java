package com.xiaojing.util;

import java.net.URL;

/**
 * Created by xiaojing on 17/2/13.
 */
public class ClassPathSearch implements PathSearcher {


  private static ClassPathSearch INSTANCE = new ClassPathSearch();

  private ClassPathSearch() {

  }

  public static ClassPathSearch getInstance() {
    return INSTANCE;
  }

  @Override
  public URL getURLByName(String name) {
    return ClassPathSearch.class.getClassLoader().getResource(name);
  }
}
