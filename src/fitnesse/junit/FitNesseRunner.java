package fitnesse.junit;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fitnesse.ConfigurationParameter;
import fitnesse.ContextConfigurator;
import fitnesse.FitNesseContext;
import fitnesse.PluginException;
import fitnesse.testrunner.MultipleTestsRunner;
import fitnesse.testrunner.PagesByTestSystem;
import fitnesse.testrunner.SuiteContentsFinder;
import fitnesse.testsystems.ConsoleExecutionLogListener;
import fitnesse.testsystems.TestSummary;
import fitnesse.wiki.PageCrawler;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPagePath;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import static org.junit.Assert.*;

public class FitNesseRunner extends ParentRunner<WikiPage> {
  /**
   * The <code>Suite</code> annotation specifies the name of the Fitnesse suite
   * (or single page) to be run, e.g.: MySuite.MySubSuite
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface Suite {

    public String value();
  }
  /**
   * The <code>DebugMode</code> annotation specifies whether the test is run
   * with the REST debug option. Default is true
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface DebugMode {

    public boolean value();
  }
  /**
   * The <code>SuiteFilter</code> annotation specifies the suite filter of the Fitnesse suite
   * to be run, e.g.: fasttests
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface SuiteFilter {

    public String value();
  }
  /**
   * The <code>ExcludeSuiteFilter</code> annotation specifies a filter for excluding tests from the Fitnesse suite
   * to be run, e.g.: slowtests
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface ExcludeSuiteFilter {

    public String value();
  }
  /**
   * The <code>FitnesseDir</code> annotation specifies the absolute or relative
   * path to the directory in which FitNesseRoot can be found. You can either specify
   * <ul>
   * <li>a relative or absolute path directly, e.g.: <code>@FitnesseDir("/parentOfFitNesseRoot")</code>,
   * or you can specify
   * <li>a system property the content of which will be taken as base dir and
   * optionally give a path extension, e.g.:
   * <code>@FitnesseDir(systemProperty = "fitnesse.root.dir.parent")</code></li>
   * </ul>
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface FitnesseDir {

    public String value() default "";
    public String systemProperty() default "";
    public String fitNesseRoot() default ContextConfigurator.DEFAULT_ROOT;
  }
  /**
   * The <code>OutputDir</code> annotation specifies where the html reports of
   * run suites and tests will be found after running them. You can either specify
   * <ul>
   * <li>a relative or absolute path directly, e.g.: <code>@OutputDir("/tmp/trinidad-results")</code>,
   * or you can specify
   * <li>a system property the content of which will be taken as base dir and
   * optionally give a path extension, e.g.:
   * <code>@OutputDir(systemProperty = "java.io.tmpdir", pathExtension = "trinidad-results")</code></li>
   * </ul>
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface OutputDir {

    public String value() default "";
    public String systemProperty() default "";

    public String pathExtension() default "";

  }
  
  /**
   * The <code>JunitXmlOutputDir</code> annotation specifies where the junit xml reports of
   * run suites and tests will be found after running them. You can either specify
   * <ul>
   * <li>a relative or absolute path directly, e.g.: <code>@OutputDir("/tmp/trinidad-results")</code>,
   * or you can specify
   * <li>a system property the content of which will be taken as base dir and
   * optionally give a path extension, e.g.:
   * <code>@OutputDir(systemProperty = "java.io.tmpdir", pathExtension = "trinidad-results")</code></li>
   * </ul>
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface JunitXmlOutputDir {

    public String value() default "";
    public String systemProperty() default "";

    public String pathExtension() default "";

  }
  /**
   * The <code>Port</code> annotation specifies the port used by the FitNesse
   * server. Default is the standard FitNesse port.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @Deprecated
  public @interface Port {

    public int value() default 0;
    public String systemProperty() default "";

  }
  /**
   * The <code>ConfigFile</code> annotation specifies the configuration file to load.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface ConfigFile {

    public String value();
  }

  private Class<?> suiteClass;
  private String suiteName;
  private String outputDir;
  private String junitXmlOutputDir;
  private String suiteFilter;
  private String excludeSuiteFilter;
  private boolean debugMode;
  private FitNesseContext context;
  private List<WikiPage> children;

  public FitNesseRunner(Class<?> suiteClass) throws InitializationError {
    super(suiteClass);
  }

  @Override
  protected void collectInitializationErrors(List<Throwable> errors) {
    // called by superclass' constructor
    super.collectInitializationErrors(errors);

    this.suiteClass = getTestClass().getJavaClass();

    try {
      this.suiteName = getSuiteName(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }

    try {
      this.outputDir = getOutputDir(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }

    try {
        this.junitXmlOutputDir = getJUnitXmlOutputDir(suiteClass);
    } catch (Throwable t) {
        errors.add(t);
    }
    
    try {
      this.suiteFilter = getSuiteFilter(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }

    try {
      this.excludeSuiteFilter = getExcludeSuiteFilter(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }

    try {
      this.debugMode = useDebugMode(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }

    try {
      this.context = createContext(suiteClass);
    } catch (Throwable t) {
      errors.add(t);
    }
  }

  protected FitNesseContext createContext(Class<?> suiteClass) throws Exception {
    String rootPath = getFitNesseDir(suiteClass);
    String fitNesseRoot = getFitNesseRoot(suiteClass);
    int port = getPort(suiteClass);
    File configFile = getConfigFile(rootPath, suiteClass);

    return initContext(configFile, rootPath, fitNesseRoot, port);
  }

  protected String getSuiteName(Class<?> klass) throws InitializationError {
    Suite suiteAnnotation = klass.getAnnotation(Suite.class);
    if (suiteAnnotation == null) {
      throw new InitializationError("There must be a @Suite annotation");
    }
    return suiteAnnotation.value();
  }

  protected String getOutputDir(Class<?> klass) throws InitializationError {
    OutputDir outputDirAnnotation = klass.getAnnotation(OutputDir.class);
    if (outputDirAnnotation == null) {
      throw new InitializationError("There must be a @OutputDir annotation");
    }
    if (!"".equals(outputDirAnnotation.value())) {
      return outputDirAnnotation.value();
    }
    if (!"".equals(outputDirAnnotation.systemProperty())) {
      String baseDir = System.getProperty(outputDirAnnotation.systemProperty());
      File outputDir = new File(baseDir, outputDirAnnotation.pathExtension());
      return outputDir.getAbsolutePath();
    }
    throw new InitializationError(
            "In annotation @OutputDir you have to specify either 'value' or 'systemProperty'");
  }

  protected String getJUnitXmlOutputDir(Class<?> klass) throws InitializationError {
	    JunitXmlOutputDir junitXmlOutputDirAnnotation = klass.getAnnotation(JunitXmlOutputDir.class);

	    if (!"".equals(junitXmlOutputDirAnnotation.value())) {
	      return junitXmlOutputDirAnnotation.value();
	    }
	    if (!"".equals(junitXmlOutputDirAnnotation.systemProperty())) {
	      String baseDir = System.getProperty(junitXmlOutputDirAnnotation.systemProperty());
	      File outputDir = new File(baseDir, junitXmlOutputDirAnnotation.pathExtension());
	      return outputDir.getAbsolutePath();
	    }
	    throw new InitializationError(
	            "In annotation @JUnitXmlOutputDir you have to specify either 'value' or 'systemProperty'");
  }
  
  protected String getSuiteFilter(Class<?> klass)
          throws Exception {
    SuiteFilter suiteFilterAnnotation = klass.getAnnotation(SuiteFilter.class);
    if (suiteFilterAnnotation == null) {
      return null;
    }
    return suiteFilterAnnotation.value();
  }

  protected String getExcludeSuiteFilter(Class<?> klass)
          throws Exception {
    ExcludeSuiteFilter excludeSuiteFilterAnnotation = klass.getAnnotation(ExcludeSuiteFilter.class);
    if (excludeSuiteFilterAnnotation == null) {
      return null;
    }
    return excludeSuiteFilterAnnotation.value();
  }

  protected boolean useDebugMode(Class<?> klass) throws Exception {
    DebugMode debugModeAnnotation = klass.getAnnotation(DebugMode.class);
    if (null == debugModeAnnotation) {
      return true;
    }
    return debugModeAnnotation.value();
  }

  protected String getFitNesseDir(Class<?> klass)
          throws InitializationError {
    FitnesseDir fitnesseDirAnnotation = klass.getAnnotation(FitnesseDir.class);
    if (fitnesseDirAnnotation == null) {
      throw new InitializationError("There must be a @FitnesseDir annotation");
    }
    if (!"".equals(fitnesseDirAnnotation.value())) {
      return fitnesseDirAnnotation.value();
    }
    if (!"".equals(fitnesseDirAnnotation.systemProperty())) {
      String baseDir = System.getProperty(fitnesseDirAnnotation.systemProperty());
      File outputDir = new File(baseDir);
      return outputDir.getAbsolutePath();
    }
    throw new InitializationError(
            "In annotation @FitnesseDir you have to specify either 'value' or 'systemProperty'");
  }
  

  protected String getFitNesseRoot(Class<?> klass) {
    FitnesseDir fitnesseDirAnnotation = klass.getAnnotation(FitnesseDir.class);
    return fitnesseDirAnnotation.fitNesseRoot();
  }

  public int getPort(Class<?> klass) throws Exception {
    Port portAnnotation = klass.getAnnotation(Port.class);
    if (null == portAnnotation) {
      return 0;
    }
    int lport = portAnnotation.value();
    if (!"".equals(portAnnotation.systemProperty())) {
      lport = Integer.getInteger(portAnnotation.systemProperty(), lport);
    }
    return lport;
  }

  protected File getConfigFile(String rootPath, Class<?> klass) throws Exception {
    ConfigFile configFileAnnotation = klass.getAnnotation(ConfigFile.class);
    if (null == configFileAnnotation) {
      return new File(rootPath, ContextConfigurator.DEFAULT_CONFIG_FILE);
    }
    return new File(configFileAnnotation.value());
  }

  @Override
  protected Description describeChild(WikiPage child) {
    return Description.createTestDescription(suiteClass, child.getPageCrawler().getFullPath().toString());
  }

  @Override
  protected List<WikiPage> getChildren() {
    if (this.children == null) {
      this.children = initChildren();
    }
    return this.children;
  }

  @Override
  public void run(final RunNotifier notifier) {
    if (isFilteredForChildTest()) {
      super.run(notifier);
    } else {
      runPages(children, notifier);
    }
  }

  private boolean isFilteredForChildTest() {
    return getDescription().getChildren().size() < getChildren().size();
  }

  @Override
  protected void runChild(WikiPage page, RunNotifier notifier) {
    runPages(listOf(page), notifier);
  }

  protected void runPages(List<WikiPage>pages, final RunNotifier notifier) {
    MultipleTestsRunner testRunner = createTestRunner(pages);
    testRunner.addTestSystemListener(new JUnitRunNotifierResultsListener(notifier, suiteClass));
    testRunner.addExecutionLogListener(new ConsoleExecutionLogListener());
    
    if (this.junitXmlOutputDir != null) {
    	testRunner.addTestSystemListener(new JUnitXMLTestListener(this.junitXmlOutputDir));
    }
    
    try {
      executeTests(testRunner);
    } catch (AssertionError e) {
      notifier.fireTestFailure(new Failure(Description.createSuiteDescription(suiteClass), e));
    } catch (Exception e) {
      notifier.fireTestFailure(new Failure(Description.createSuiteDescription(suiteClass), e));
    }
  }

  protected List<WikiPage> initChildren() {
    WikiPage suiteRoot = getSuiteRootPage();
    if (suiteRoot == null) {
      throw new IllegalArgumentException("No page " + this.suiteName);
    }
    List<WikiPage> children;
    if (suiteRoot.getData().hasAttribute("Suite")) {
      children = new SuiteContentsFinder(suiteRoot, new fitnesse.testrunner.SuiteFilter(suiteFilter, excludeSuiteFilter), context.root).getAllPagesToRunForThisSuite();
    } else {
      children = Collections.singletonList(suiteRoot);
    }
    return children;
  }

  static FitNesseContext initContext(File configFile, String rootPath, String fitNesseRoot, int port) throws IOException, PluginException {
    ContextConfigurator contextConfigurator = ContextConfigurator.systemDefaults()
      .updatedWith(System.getProperties())
      .updatedWith(ConfigurationParameter.loadProperties(configFile))
      .updatedWith(ConfigurationParameter.makeProperties(
            ConfigurationParameter.PORT, port,
            ConfigurationParameter.ROOT_PATH, rootPath,
            ConfigurationParameter.ROOT_DIRECTORY, fitNesseRoot,
            ConfigurationParameter.OMITTING_UPDATES, true));

    return contextConfigurator.makeFitNesseContext();
  }

  private WikiPage getSuiteRootPage() {
    WikiPagePath path = PathParser.parse(this.suiteName);
    PageCrawler crawler = context.root.getPageCrawler();
    return crawler.getPage(path);
  }

  private MultipleTestsRunner createTestRunner(List<WikiPage> pages) {
    final PagesByTestSystem pagesByTestSystem = new PagesByTestSystem(pages, context.root, context.variableSource);

    MultipleTestsRunner runner = new MultipleTestsRunner(pagesByTestSystem,
            context.testSystemFactory);
    runner.setRunInProcess(debugMode);
    return runner;
  }

  private void executeTests(MultipleTestsRunner testRunner) throws IOException, InterruptedException {
    JavaFormatter testFormatter = new JavaFormatter(suiteName);
    testFormatter.setResultsRepository(new JavaFormatter.FolderResultsRepository(outputDir));
    testRunner.addTestSystemListener(testFormatter);

    testRunner.executeTestPages();
    TestSummary summary = testFormatter.getTotalSummary();

    assertEquals("wrong", 0, summary.getWrong());
    assertEquals("exceptions", 0, summary.getExceptions());
    assertTrue(msgAtLeastOneTest(suiteName, summary), summary.getRight() > 0);
  }

  private String msgAtLeastOneTest(String pageName, TestSummary summary) {
    return MessageFormat.format("at least one test executed in {0}\n{1}",
            pageName, summary.toString());
  }

  private List<WikiPage> listOf(WikiPage page) {
    List<WikiPage> list = new ArrayList<WikiPage>(1);
    list.add(page);
    return list;
  }
}
