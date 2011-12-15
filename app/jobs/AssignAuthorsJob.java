package jobs;

import java.util.Iterator;
import java.util.List;

import javax.persistence.FlushModeType;

import models.PCAProgram;
import play.Logger;
import play.jobs.Job;

/**
 * This job updates the author info of programs if they need it.
 * 
 * @author james.kennedy
 * 
 */
public class AssignAuthorsJob extends Job {

    private static int UPDATES_PER_JOB = 1000;

    @Override
    public void doJob() {
        List<PCAProgram> programsToUpdate = getProgramsThatNeedUpdate();
        if (programsToUpdate.isEmpty()) {
            Logger.info("STOP: All programs have updated authors.");
            return;
        }
        Logger.info("START: Updating authors of %d programs...", programsToUpdate.size());

        PCAProgram.em().setFlushMode(FlushModeType.COMMIT);

        Iterator<PCAProgram> itr = programsToUpdate.iterator();
        for (int i = 0; i < UPDATES_PER_JOB && itr.hasNext(); i++) {
            PCAProgram program = itr.next();
            program.calculateAuthors();
            itr.remove();
        }

        // Reschedule self while there are updates left to be done
        if (!programsToUpdate.isEmpty() || !getProgramsThatNeedUpdate().isEmpty()) {
            this.now();
        } else {
            Logger.info("STOP: All programs have updated authors.");
        }
    }

    private List<PCAProgram> getProgramsThatNeedUpdate() {
        return PCAProgram.find("needsAuthorUpdate != ?", Boolean.FALSE).fetch(UPDATES_PER_JOB);
    }

}
