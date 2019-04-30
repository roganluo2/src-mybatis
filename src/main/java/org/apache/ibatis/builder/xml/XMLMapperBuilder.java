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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
//解析mapper.xml文件
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  //解析mapper的入口
  public void parse() {
//    判断是否已经加载过该映射文件
    if (!configuration.isResourceLoaded(resource)) {
//      处理mapper节点
      configurationElement(parser.evalNode("/mapper"));
//      将resource添加到configuration。loadedresources集合中保存，他是hashset<string>类型的集合，其中记录了已经加载过的映射文件
      configuration.addLoadedResource(resource);
//      注册mapper接口
      bindMapperForNamespace();
    }
//    处理configurationelement方法中解析失败的resultmap节点
    parsePendingResultMaps();
//    处理configurationelement方法中解析失败的cache-ref 节点
    parsePendingCacheRefs();
//    处理configurationelement方法中解析失败的sql语句节点
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  private void configurationElement(XNode context) {
    try {
//      获取namespace属性
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
//        如果那么space为空，抛出异常
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
//      设置mapperbuilderassistant的currentnamespace字段，记录当前命名空间
      builderAssistant.setCurrentNamespace(namespace);
//      解析cache-ref
      cacheRefElement(context.evalNode("cache-ref"));
//      解析cache
      cacheElement(context.evalNode("cache"));
//      解析parametermap节点，当前已经废弃
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
//      解析resultmap
      resultMapElements(context.evalNodes("/mapper/resultMap"));
//      解析sql节点
      sqlElement(context.evalNodes("/mapper/sql"));
//      解析select 。。。
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

//  用于多个namespace共用一个二级缓存，cahce-ref就派上用场
  private void cacheRefElement(XNode context) {
    if (context != null) {
//      将当前mapper配置文件namespace与被引用的cache所在的namesapce之间的对应关系
//      记录到configuration cacherefmap集合中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
//      创建cacherefresolver对象
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
//        解析cache引用，该过程主要是设置mapperbuilderassistant
//        中的currentcache,和unresolvedCacheRef
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
//        如果解析过程出现异常，则添加到configuration.incompleteCacheRef集合
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
//      获取cache节点的type属性，默认是prepetual
      String type = context.getStringAttribute("type", "PERPETUAL");
//      查找type属性对应的cache接口实现，typealiasregistry
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
//      获取cache节点的eviction属性，默认是LRU
      String eviction = context.getStringAttribute("eviction", "LRU");
//      解析eviction属性指定的cache装饰类型
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
//      获取cache节点的flushinterval属性，默认是null
      Long flushInterval = context.getLongAttribute("flushInterval");
//      cache节点的size，默认null
      Integer size = context.getIntAttribute("size");
//      获取cache几点的readonly属性，默认是false
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
//      获取blocking属性，默认是false
      boolean blocking = context.getBooleanAttribute("blocking", false);
//      获取cache节点下的子节点，用于初始化二级缓存
      Properties props = context.getChildrenAsProperties();
//      通过mapperbuilderassistant创建cache对象，并添加到configuration。caches集合中保存
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  //版本中会移除，建议不使用
  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
//    获取resultMap的id属性，默认值会拼装所有父节点的id或者value或者property属性值，
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
//    获取resultMap节点的type属性，标识结果集将被映射成type指定类型的对象，注意其默认值
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
//    获取resultmap节点的extends属性，该属性指定了该resultMap节点的继承关系，后面详细介绍
    String extend = resultMapNode.getStringAttribute("extends");
//    读取resultMap节点的automapping属性，将该属性设置为true，则启动自动映射功能，
//    即自动查找列名同名的属性名，并调用setter方法，而设置为false后，则需要在resultMap节点内明确注明关系才会调用对应的setter方法
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
//    解析type类型
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
//    该集合用于记录解析的结果
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);
//    处理resultMap的子节点
    List<XNode> resultChildren = resultMapNode.getChildren();

    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
//        处理constructor节点
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
//        处理discriminator
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
//        处理id result association collection等节点
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
//          如果是id节点，则向flags集合中添加resultFag.ID
          flags.add(ResultFlag.ID);
        }
//        创建resultMapping对象，并添加resultMappings集合中保存
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
//      创建resultmap对象，并添加到configuration.resultMaps集合中，该集合是strictMap类型
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
//    获取constructor节点的子节点
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
//      添加constructor标志
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
//        对于idarg节点，添加id标志
        flags.add(ResultFlag.ID);
      }
//      创建resultMapping对象，并添加到resultMapping集合中
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
//    获取column javaType jdbcType typeHandler
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
//    处理discriminator节点的子节点
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
//      调用processNestedResultMappings()方法创建嵌套的resultMap对象
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
//      记录该列值与对应选择的resultMap的id
      discriminatorMap.put(value, resultMap);
    }
//    创建discriminator对象
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
//      获取databaseId属性
      String databaseId = context.getStringAttribute("databaseId");
//      获取id属性
      String id = context.getStringAttribute("id");
//      为id添加命名空间
      id = builderAssistant.applyCurrentNamespace(id, false);
//      加测sql的databaseId与当前configuration中记录的databaseId是否一致
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
//        记录到xmlMapperBuilder.sqlFragments(Map<String, XNode>)中保存
//        在xmlMapperBuilder的构造函数中，可以看到该字段指向了Configuration.sqlFragments集合
        sqlFragments.put(id, context);
      }
    }
  }
  
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
//      获取该节点的property的属性值
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
//    如果未指定association节点的resultmap属性，则匿名的嵌套映射，需要通过processNestedResultMappings()
//    方法解析该匿名的嵌套映射，在后面分析collection节点是还会涉及匿名嵌套映射的解析过程
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
//    解析javaType typeHandler和jdbcType
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
//    创建ResultMapping对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }
  
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
//    只会处理association collection case三种节点
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
//      指定了select属性之后，不会生产嵌套的resultmap对象
      if (context.getStringAttribute("select") == null) {
//        创建resultMap对象，并添加到configuration.resultMaps集合，注意，本例中association节点中没有id
//        其id由xnode.getValueBasedIdentifier方法生成，本例中，id为mapper_resultMap[detailedBlogResultMap]_association[author]
//        另外,本例中的assocation节点指定了resultMap属性，而非匿名的嵌套映射，所以该resultMap对象中的resultMappings集合为空
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }

  private void bindMapperForNamespace() {
//    获取映射配置文件的命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
//        解析命名空间对应的类型
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
//        是否已经加载了boundType接口
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
//          追加namespace前缀，并添加到Configuration.loadedResources集合中保存
          configuration.addLoadedResource("namespace:" + namespace);
//          调用mapperregistry.addMapper()方法，注册boundType接口
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
