package git;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.GITRepository;
import models.RepoCommit;
import play.Logger;
import play.jobs.Job;
import services.PCALinkageService;
import edu.nyu.cs.javagit.api.DotGit;
import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.commands.GitLogOptions;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.CommitFile;

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
        gitLogOptions.setOptFileDetails(true);

        // TODO: How come this doesn't work?
        if (repo.lastCommitParsed != null) {
            // gitLogOptions.setOptLimitCommitSince(true,
            // repo.lastCommitParsed);
        }

        // TODO: DEBUG
        // gitLogOptions.setOptLimitCommitMax(true, 200);

        List<Commit> commitList = null;
        try {
            Logger.info("Preparing to scan git log...");
            commitList = dotGit.getLog(gitLogOptions);
        } catch (JavaGitException e) {
            Logger.error(e, "Cannot synchronize with GITrepository %s.", repo.location);
        } catch (IOException e) {
            Logger.error(e, "IOException synchronizing with GIT repo %s.", repo.location);
        }

        int newCommits = 0;
        Set<String> filesCommitted = new HashSet<String>();
        for (Commit commit : commitList) {
            try {
                boolean processed = processCommit(commit, filesCommitted);
                if (processed) {
                    newCommits++;
                }
                repo.lastCommitParsed = commit.getSha();
            } catch (ParseException e) {
                Logger.error(e, "Failed to parse commit %s", commit.getSha());
                break;
            }
        }

        Logger.info("Processed %d new commits.\nUpdating PCA program file linkage...", newCommits);

        repo.save();

        PCALinkageService.updateFileLinkage(repo, filesCommitted);

        Logger.info("Synchronization complete.");
    }

    boolean processCommit(final Commit commit, final Set<String> filesCommitted) throws ParseException {
        Logger.debug("%s %s", commit.getDateString(), commit.getAuthor());
        RepoCommit repoCommit = RepoCommit.find("bySha", commit.getSha()).first();
        if (null == repoCommit) {
            repoCommit = new RepoCommit(commit);
            if (!repoCommit.sharedInRemoteRepository()) {
                // Skip commits that local only and may still change
                return false;
            }

            if (commit.getFiles() != null) {
                for (CommitFile commitFile : commit.getFiles()) {
                    filesCommitted.add(commitFile.getName());
                }
            }

            repoCommit.save();

            PCALinkageService.linkCommitToPrograms(repoCommit);

            return true;
        }

        return false;
    }
}
