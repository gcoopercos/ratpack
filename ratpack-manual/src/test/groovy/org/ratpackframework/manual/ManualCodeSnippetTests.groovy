package org.ratpackframework.manual

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.ratpackframework.manual.snippets.CodeSnippetTestCase
import org.ratpackframework.manual.snippets.CodeSnippetTests
import org.ratpackframework.manual.snippets.TestCodeSnippet
import org.ratpackframework.manual.snippets.fixtures.GroovyChainDslFixture
import org.ratpackframework.manual.snippets.fixtures.GroovyHandlersFixture
import org.ratpackframework.manual.snippets.fixtures.GroovyRatpackDslFixture
import org.ratpackframework.manual.snippets.fixtures.SnippetFixture
import org.ratpackframework.util.Transformer

import java.util.regex.Pattern

class ManualCodeSnippetTests extends CodeSnippetTestCase {

  @Override
  protected void addTests(CodeSnippetTests tests) {
    File cwd = new File(System.getProperty("user.dir"))
    File root
    if (new File(cwd, "ratpack-manual.gradle").exists()) {
      root = cwd.parentFile
    } else {
      root = cwd
    }

    def content = new File(root, "ratpack-manual/src/content/chapters")

    [
      "language-groovy groovy-chain-dsl": new GroovyChainDslFixture(),
      "language-groovy groovy-ratpack": new GroovyRatpackDslFixture(),
      "language-groovy groovy-handlers": new GroovyHandlersFixture()
    ].each { selector, fixture ->
      ManualSnippetExtractor.extract(content, selector, fixture).each {
        tests.add(it)
      }
    }
  }

  static class ManualSnippetExtractor {

    static List<TestCodeSnippet> extract(File root, String cssClass, SnippetFixture fixture) {
      List<TestCodeSnippet> snippets = []

      def snippetBlockPattern = Pattern.compile(/(?ims)```$cssClass\n(.*?)\n```/)
      def filenames = new FileNameFinder().getFileNames(root.absolutePath, "*.md")

      filenames.each { filename ->
        def file = new File(filename)
        addSnippets(snippets, file, snippetBlockPattern, fixture)
      }

      snippets
    }

    private static void addSnippets(List<TestCodeSnippet> snippets, File file, Pattern snippetBlockPattern, SnippetFixture snippetFixture) {
      def source = file.text
      String testName = file.name
      Map<Integer, String> snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

      snippetsByLine.each { lineNumber, snippet ->
        snippets << createSnippet(testName, file, lineNumber, snippet, snippetFixture)
      }
    }

    private static List<String> findSnippetBlocks(String code, Pattern snippetTagPattern) {
      List<String> tags = []
      code.eachMatch(snippetTagPattern) { matches ->
        tags.add(matches[0])
      }
      tags
    }

    private static Map<Integer, String> findSnippetsByLine(String source, Pattern snippetTagPattern) {
      List<String> snippetBlocks = findSnippetBlocks(source, snippetTagPattern)
      Map snippetBlocksByLine = [:]

      int codeIndex = 0
      snippetBlocks.each { block ->
        codeIndex = source.indexOf(block, codeIndex)
        def lineNumber = source.substring(0, codeIndex).readLines().size() + 1
        snippetBlocksByLine.put(lineNumber, extractSnippetFromBlock(block))
        codeIndex += block.size()
      }

      snippetBlocksByLine
    }

    private static String extractSnippetFromBlock(String tag) {
      tag.substring(tag.indexOf("\n") + 1, tag.lastIndexOf("\n"))
    }

    private static TestCodeSnippet createSnippet(String sourceClassName, File sourceFile, int lineNumber, String snippet, SnippetFixture fixture) {
      new TestCodeSnippet(snippet, sourceClassName, sourceClassName + ":$lineNumber", fixture, new Transformer<Throwable, Throwable>() {
        @Override
        Throwable transform(Throwable t) {
          def errorLine = 0

          if (t instanceof MultipleCompilationErrorsException) {
            def compilationException = t as MultipleCompilationErrorsException
            def error = compilationException.errorCollector.getError(0)
            if (error instanceof SyntaxErrorMessage) {
              errorLine = error.cause.line
            }
          } else {
            def frame = t.getStackTrace().find { it.fileName == sourceClassName }
            if (frame) {
              errorLine = frame.lineNumber
            }
          }
          errorLine = errorLine - fixture.pre().split("\n").size()
          StackTraceElement[] stack = t.getStackTrace()
          List<StackTraceElement> newStack = new ArrayList<StackTraceElement>(stack.length + 1)
          newStack.add(new StackTraceElement(sourceClassName, "javadoc", sourceFile.name, lineNumber + errorLine))
          newStack.addAll(stack)
          t.setStackTrace(newStack as StackTraceElement[])

          t
        }
      })

    }

  }
}
