package io.helidon.tools.config.metadata;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ConfigDocumentationTest {
    @Test
    void testHtmlParagraph() {
        String original = """
                First
                <p>some text
                 <p>some text<p>some text
                Some other text
                """;
        String expected = """
                First
                some text
                some text
                some text
                Some other text
                """;
        String actual = ConfigDocumentation.translateHtml(original);
        assertThat(actual, is(expected));
    }

    @Test
    void testHtmlUl() {
        String original = """
                First ul: <ul><li>text</li><li>text</li></ul>
                Second ul:
                <ul>
                  <li>text</li>
                  <li>text</li>
                 </ul>
                """;
        String expected = """
                First ul:
                
                - text
                - text
                
                Second ul:
                
                - text
                - text
                                
                """;
        String actual = ConfigDocumentation.translateHtml(original);
        assertThat(actual, is(expected));
    }

    @Test
    void testAtValue() {
        String original = """
                Some value: {@value #DEFAULT_BASE_SCOPE}
                Some value: {@value SomeType#SOME_CONSTANT}
                """;
        String expected = """
                Some value: `DEFAULT_BASE_SCOPE`
                Some value: `SomeType#SOME_CONSTANT`
                """;
        String actual = ConfigDocumentation.translateHtml(original);
        assertThat(actual, is(expected));
    }
}