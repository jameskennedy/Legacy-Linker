package app.models;
import org.junit.*;
import java.util.*;
import play.test.*;
import models.*;

public class PCAProgramModelTest extends UnitTest {

	@Before
    public void setup() {
        Fixtures.deleteDatabase();
    }
    
    @Test
    public void createAndRetrieveProgram() {
        // Create a new user and save it
        PCAProgram expected = new PCAProgram("MRCPSO06", "Service order assign details screen.", "jkennedy").save();
        
        // Retrieve the user with e-mail address bob@gmail.com
        PCAProgram pg = (PCAProgram) PCAProgram.find("Name like ?", "%SO06%").fetch(1).get(0);
        
        // Test 
        assertNotNull(pg);
        assertEquals(expected.name, pg.name);
        assertEquals(expected.description, pg.description);
        assertEquals(expected.author, pg.author);
    }

}
