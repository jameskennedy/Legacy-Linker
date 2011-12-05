package git;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import models.GITRepository;
import models.RepoCommit;
import play.Logger;
import play.jobs.Job;
import edu.nyu.cs.javagit.api.DotGit;
import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.commands.GitLogOptions;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;

public class ParseRepositoryJob extends Job {

    @Override
    public void doJob() {
        GITRepository repo = GITRepository.findById(GITRepository.getMainRepository().getId());

        if (repo.location == null) {
            Logger.error("Cannot synchronize with repository that has a null location.");
            return;
        }

        Logger.info("Synchronizing history with %s...", repo.location);

        File repositoryDirectory = new File(repo.location);
        DotGit dotGit = DotGit.getInstance(repositoryDirectory);

        if (!repositoryDirectory.exists() || !dotGit.existsInstance(repositoryDirectory)) {
            Logger.error("Cannot synchronize with non-existant repository %s.", repo.location);
            return;
        }

        GitLogOptions gitLogOptions = new GitLogOptions();
        gitLogOptions.setOptOrderingReverse(true);

        // TODO: How come this doesn't work?
        if (repo.lastCommitParsed != null) {
            gitLogOptions.setOptLimitCommitSince(true, repo.lastCommitParsed);
        }

        // TODO: DEBUG
        // gitLogOptions.setOptLimitCommitMax(true, 20);

        List<Commit> commitList = null;
        try {
            commitList = dotGit.getLog(gitLogOptions);
        } catch (JavaGitException e) {
            Logger.error(e, "Cannot synchronize with GITrepository %s.", repo.location);
        } catch (IOException e) {
            Logger.error(e, "IOException synchronizing with GIT repo %s.", repo.location);
        }

        int newCommits = 0;
        for (Commit commit : commitList) {
            try {
                boolean processed = processCommit(commit);
                if (processed) {
                    newCommits++;
                }
            } catch (ParseException e) {
                Logger.error(e, "Failed to parse commit %s", commit.getSha());
                break;
            }

            repo.lastCommitParsed = commit.getSha();
        }

        repo.save();

        Logger.info("Synchronization complete with %d new commits.", newCommits);
    }

    boolean processCommit(final Commit commit) throws ParseException {
        Logger.info("%s %s", commit.getSha(), commit.getDateString());
        RepoCommit repoCommit = RepoCommit.find("bySha", commit.getSha()).first();
        if (null == repoCommit) {
            repoCommit = new RepoCommit(commit);
            if (!repoCommit.sharedInRemoteRepository()) {
                // Skip commits that local only and may still change
                return false;
            }
            repoCommit.save();
            return true;
        }

        return false;
    }
}
