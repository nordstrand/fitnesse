package fitnesse.junit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import fitnesse.testrunner.WikiTestPage;
import fitnesse.testsystems.Assertion;
import fitnesse.testsystems.ExceptionResult;
import fitnesse.testsystems.TestResult;
import fitnesse.testsystems.TestSummary;
import fitnesse.testsystems.TestSystem;
import fitnesse.testsystems.TestSystemListener;
import fitnesse.util.TimeMeasurement;
import fitnesse.wiki.WikiPagePath;

public class JUnitXMLTestListener implements TestSystemListener<WikiTestPage> {
  
  private String outputPath;
  private TimeMeasurement timeMeasurement;

  public JUnitXMLTestListener(String outputPath) {
    this.outputPath=outputPath;
    new File(outputPath).mkdirs();
  }
  
  public void recordTestResult(String testName, TestSummary result, long executionTime) throws IOException {
    int errors = 0;
    int failures = 0;
    String failureXml = "";
    
    if (result.getExceptions() + result.getWrong() > 0) {
      failureXml = "<failure type=\"java.lang.AssertionError\" message=\"" + " exceptions: "
          + result.getExceptions() + " wrong: " + result.getWrong() + "\"></failure>";
      if (result.getExceptions() > 0)
        errors = 1;
      else
        failures = 1;
    }

    String resultXml = "<testsuite errors=\"" + errors + "\" skipped=\"0\" tests=\"1\" time=\""
        + executionTime / 1000d + "\" failures=\"" + failures + "\" name=\""
        + testName + "\">" + "<properties></properties>" + "<testcase classname=\""
        + testName + "\" time=\"" + executionTime / 1000d + "\" name=\""
        + testName + "\">" + failureXml + "</testcase>" + "</testsuite>";

    String finalPath = new File(outputPath, "TEST-" + testName + ".xml").getAbsolutePath();
    FileWriter fw = new FileWriter(finalPath);
    fw.write(resultXml);
    fw.close();
  }

  @Override
  public void testStarted(WikiTestPage test) {
    timeMeasurement = new TimeMeasurement().start();
  }

  @Override
  public void testComplete(WikiTestPage test, TestSummary testSummary) throws IOException {
    recordTestResult(new WikiPagePath(test.getSourcePage()).toString(), testSummary, timeMeasurement.elapsed());
  }

  @Override
  public void testOutputChunk(String output)  {
  }

  @Override
  public void testAssertionVerified(Assertion assertion, TestResult testResult) {
  }

  @Override
  public void testExceptionOccurred(Assertion assertion, ExceptionResult exceptionResult) {
  }

  @Override
  public void testSystemStarted(TestSystem testSystem) {
  }

  @Override
  public void testSystemStopped(TestSystem testSystem, Throwable cause) {
  }
}
