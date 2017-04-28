package com.xiaojing.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by xiaojing on 17/2/13.
 */
public class FilePathSearcher implements PathSearcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(FilePathSearcher.class);

  private static final String PATH_SEPARATOR = ",";
  public static final String PROTOCOL = "file:";

  private static FilePathSearcher INSTANCE = new FilePathSearcher();

  private FilePathSearcher(){

  }

  public static FilePathSearcher getInstance(){
    return INSTANCE;
  }

  @Override
  public URL getURLByName(String fileName) {

    String configDirs = System.getProperty("CONFIG_DIRS");
    String configDir = System.getProperty("CONFIG_DIR");
    String[] directories;

    if (StringUtils.isNotBlank(configDirs)) {
      directories = configDirs.split(PATH_SEPARATOR);
    }else if(StringUtils.isNotBlank(configDir)){
      directories = configDir.split(PATH_SEPARATOR);//没有配置优先级,则默认在对应的环境对应的文件夹下查找
    }else {
      return null;
    }



    String urlString = null;

    //根据优先级进行查找
    for (String directory : directories) {
      String filePath = directory + File.separator + fileName;
      File file = new File(filePath);
      if (file.isFile()) {
        urlString =  PROTOCOL+filePath;
        break;
      }
    }

    if(StringUtils.isNotBlank(urlString)){
      try{
        return new URL(urlString);
      }catch (MalformedURLException mfe){
        LOGGER.error("generate url from path string fail,urlString={}", urlString);
      }
    }

    return null;
  }
}
