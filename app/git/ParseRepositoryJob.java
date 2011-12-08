package git;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import models.GITRepository;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;
import play.db.jpa.NoTransaction;
import play.jobs.Job;
import services.PCALinkageService;
import edu.nyu.cs.javagit.api.DotGit;
import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.commands.GitLogOptions;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.CommitFile;

public class ParseRepositoryJob extends Job {

    @Override
    @NoTransaction
    public void doJob() {
        long startTime = System.currentTimeMillis();

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
        gitLogOptions.setOptLimitCommitMax(true, 80);

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

        // Using sorted set t o access files more contiguously.
        Set<RepoFile> filesCommitted = new TreeSet<RepoFile>();

        for (Commit commit : commitList) {
            try {
                RepoCommit processed = processCommit(commit, filesCommitted, repo);
                if (processed == null) {
                    continue;
                }

                newCommits++;
                if (repo.svnRevision == null || (repo.svnRevision < processed.svnRevision)) {
                    repo.svnRevision = processed.svnRevision;
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

        long endTime = System.currentTimeMillis();
        long jobTime = (endTime - startTime) / 1000;
        Logger.info("Synchronization complete. Took " + jobTime + " seconds.");
    }

    RepoCommit processCommit(final Commit commit, final Set<RepoFile> filesCommitted, final GITRepository repo)
                    throws ParseException {
        Logger.debug("%s %s", commit.getDateString(), commit.getAuthor());
        RepoCommit repoCommit = RepoCommit.find("bySha", commit.getSha()).first();
        if (null == repoCommit) {
            repoCommit = new RepoCommit(commit);
            if (!repoCommit.sharedInRemoteRepository()) {
                // Skip commits that local only and may still change
                return null;
            }

            if (commit.getFiles() != null) {
                for (CommitFile commitFile : commit.getFiles()) {
                    String path = commitFile.getName();

                    if (!path.endsWith(".java")) {
                        Logger.trace("Ignoring file %s", path);
                        continue;
                    }

                    RepoFile repoFile = RepoFile.find("byRepositoryAndPath", repo, path).first();
                    if (null == repoFile) {
                        repoFile = new RepoFile(repo, path);
                        repoFile.commits = new ArrayList<RepoCommit>();
                        repoFile.save();
                    }

                    // Associate the commit
                    repoFile.commits.add(repoCommit);
                    filesCommitted.add(repoFile);
                }
            }

            repoCommit.save();

            PCALinkageService.linkCommitToPrograms(repoCommit);

            return repoCommit;
        }

        return null;
    }
}
