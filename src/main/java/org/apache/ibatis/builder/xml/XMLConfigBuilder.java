/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
//是baseBuilder的众多子类之一，扮演具体建造者的角色
public class XMLConfigBuilder extends BaseBuilder {

//  标识是否已经解析过mybatis-config.xml配置文件
  private boolean parsed;
//  用于解析mybatis-config.xml配置文件的XpathParser对象，
  private final XPathParser parser;
//  默认读取<environment>标签的default属性
  private String environment;

//  负责创建和缓存reflector对象，
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }


  public Configuration parse() {
//    根据parse的值判断是否已经完成了对mybatis-config.xml配置文件的解析
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
//    在mybatis-config.xml配置文件中查找configuration，并开始解析
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
//      解析properties节点
      propertiesElement(root.evalNode("properties"));
//      解析setting节点
      Properties settings = settingsAsProperties(root.evalNode("settings"));
//      设置vfsImpl字段？？
      loadCustomVfs(settings);
//      解析typeAliases节点
      typeAliasesElement(root.evalNode("typeAliases"));
//      解析plugins节点
      pluginElement(root.evalNode("plugins"));
//      解析objectFactory节点
      objectFactoryElement(root.evalNode("objectFactory"));
//      解析objectWrapperFactory节点
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
//      解析reflectorfactory节点
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
//      将setting设置到configuration中
      settingsElement(settings);

      // read it after objectFactory and objectWrapperFactory issue #631
//      解析environment 节点
      environmentsElement(root.evalNode("environments"));
//      解析databaseIdprovider节点
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
//      解析typehandler节点
      typeHandlerElement(root.evalNode("typeHandlers"));
//      解析mapper节点
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

//  解析setting节点，mybatis的全局配置，如通过配置automappingbehavior修改mybatis是否开启自动映射功能
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
//    解析setting的子节点，的name和value属性，并返回properties对象。
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
//    创建configuration对应的metaclass对象
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
//    检测configuration是否定义了key指定属性相应的setter方法
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

//  解析typealiases typehandler节点
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
//      处理全部子节点
      for (XNode child : parent.getChildren()) {
//        处理package节点
        if ("package".equals(child.getName())) {
//          获取指定的包名
          String typeAliasPackage = child.getStringAttribute("name");
//          通过typealiasregisterry扫描指定包中所有的类，并解析@Alias注解，完成别名注册
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
//          c处理typealias节点
//          获取指定别名
          String alias = child.getStringAttribute("alias");
//          获取别名对应的类型
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
//              扫描@Alias注解，完成注册
              typeAliasRegistry.registerAlias(clazz);
            } else {
//              注册别名
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

//  解析插件
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
//      遍历全部子节点，<plugin>
      for (XNode child : parent.getChildren()) {
//        获取plugin节点的interceptor属相的值
        String interceptor = child.getStringAttribute("interceptor");
//        获取plugin节点下properties配置信息，并形成properties对象
        Properties properties = child.getChildrenAsProperties();
//        通过前面介绍的typealiasregistry解析别名后，实例化interceptor对象
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
//        设置interceptor的属性
        interceptorInstance.setProperties(properties);
//        记录interceptor对象
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
//      获取objectfactory节点的type属性
      String type = context.getStringAttribute("type");
//      获取objectfactory节点下的配置信息，并形成properties
      Properties properties = context.getChildrenAsProperties();
//      进行别名解析后，实例化自定义objectfactory实现
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
//      设置自定义objectfactory的属性，完成初始化相关操作
      factory.setProperties(properties);
//      将自定义objectfactory对象记录到configuration对象的objectfactory字段中，
      configuration.setObjectFactory(factory);
    }
  }

//  对objectwrapperfactory节点 reflectorfactory节点的解析与上述过程类似
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

//  解析propertis节点，形成properties对象，然后将对象设置到XpathParser对象和configuration的variables中，
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
//      解析properties的子节点，<property>的namevalue属性，记录到properties中
      Properties defaults = context.getChildrenAsProperties();
//      解析properties的resource和url属性，这两个属性用于确定properties配置文件的位置
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
//      加载resource属性或url属性,指定的properties文件，
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
//      与configuration对象中variables集合合并
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) throws Exception {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler> typeHandler = (Class<? extends TypeHandler>)resolveClass(props.getProperty("defaultEnumTypeHandler"));
    configuration.setDefaultEnumTypeHandler(typeHandler);
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    @SuppressWarnings("unchecked")
    Class<? extends Log> logImpl = (Class<? extends Log>)resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  //同一项目有开发 测试 生产多个环境，每个环境的配置不同
//  根据xmlconfigbuilder.environment字段确定要使用的environment配置，之后创建对应的transactionfactory和datasourse对象，并封装进environment对象中
  private void environmentsElement(XNode context) throws Exception {

    if (context != null) {
//      未指定xmlconfigbuilder。environment字段，使用default属性指定的environment
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
//      遍历自己点，即environment节点
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
//        与xmlconfigbuilder。environment字段匹配
        if (isSpecifiedEnvironment(id)) {
//          创建transactionfactory，具体实现是先通过typealiasregistry解析别名之后
//          实例化transactionfactory
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));

//          创建datasourcefactory和DataSource
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
//          创建environment，封装了上面的transactionfactory和datasource对象,这里用到了建造者模式
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
//          将environment对象记录到configuration.environment字段中
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

//  通过databaseidprovider定义的所有支持的数据库产品的databaseid,然后在配置文件中定义sql语句节点时,通过databaseid指定该sql
//  语句应用的数据库产品
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
//      为了保证兼容性,修改type取值
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
//      解析相关配置信息
      Properties properties = context.getChildrenAsProperties();
//      创建databaseidprovider对象
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
//      配置databaseidprovider，完成初始化
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
//      通过前面确定的datasource获取databaseid，并记录到configuration。databaseid字段中
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

//  解析mapper节点
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
//      处理mapper的子节点
      for (XNode child : parent.getChildren()) {
//        package子节点
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
//          扫描指定的包，并向mapperregistry注册mapper接口
          configuration.addMappers(mapperPackage);
        } else {
//          获取mapper 节点的resource url class属性，这三个属性互斥
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
//          如果mapper节点指定了resource或者url属性，则创建xmlampperbuilder对象，
//          并通过该对象解析resource或是url属性指定的mapper配置文件
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
//            创建xmlmapperbuilder对象，解析映射配置文件
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
//            创建xmlmapperbuilder对象，解析映射配置文件
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
//            如果mapper 节点指定了class属性，则指向mapperregistry注册该mapper接口
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
