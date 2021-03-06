package net.seesharpsoft.intellij.plugins.csv.settings;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class CsvCodeStyleSettingsTest extends LightCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "./src/test/resources/settings";
    }

    public void testDefaultSeparator() {
        assertEquals(CsvCodeStyleSettings.DEFAULT_SEPARATOR, CsvCodeStyleSettings.getCurrentSeparator(myFixture.getProject()));
    }

    public void testFileDefaultSeparator() {
        myFixture.configureByFiles("AnyFile.csv");

        assertEquals(CsvCodeStyleSettings.DEFAULT_SEPARATOR, CsvCodeStyleSettings.getCurrentSeparator(myFixture.getFile()));
    }
}
