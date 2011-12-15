package app.models;

import models.PCAProgram;

import org.junit.Before;
import org.junit.Test;

import play.test.Fixtures;
import play.test.UnitTest;

public class PCAProgramModelTest extends UnitTest {

    @Before
    public void setup() {
        Fixtures.deleteDatabase();
    }

    @Test
    public void createAndRetrieveProgram() {
        // Create a new user and save it
        PCAProgram expected = new PCAProgram("MRCPSO06", "Service order assign details screen.").save();

        // Retrieve the user with e-mail address bob@gmail.com
        PCAProgram pg = (PCAProgram) PCAProgram.find("Name like ?", "%SO06%").fetch(1).get(0);

        // Test
        assertNotNull(pg);
        assertEquals(expected.name, pg.name);
        assertEquals(expected.description, pg.description);
    }

}
