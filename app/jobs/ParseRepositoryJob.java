package jobs;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import models.GITRepository;
import models.RepoCommit;
import models.RepoFile;
import models.RepoFileCommit;
import play.Logger;
import play.jobs.Every;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import edu.nyu.cs.javagit.api.DotGit;
import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.commands.GitLogOptions;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.CommitFile;

@OnApplicationStart
@Every("10mn")
public class ParseRepositoryJob extends Job {

    @Override
    public void doJob() {
        long startTime = System.currentTimeMillis();

        Logger.info("START: Git repository sync...");

        GITRepository repo = GITRepository.getMainRepository();

        if (repo.location == null) {
            Logger.error("STOP: Cannot synchronize with repository that has a null location.");
            return;
        }

        Logger.debug("Synchronizing history with %s...", repo.location);

        File repositoryDirectory = new File(repo.location);
        DotGit dotGit = DotGit.getInstance(repositoryDirectory);

        if (!repositoryDirectory.exists() || !dotGit.existsInstance(repositoryDirectory)) {
            Logger.error("STOP: Cannot synchronize with non-existant repository %s.", repo.location);
            return;
        }

        GitLogOptions gitLogOptions = new GitLogOptions();
        gitLogOptions.setOptOrderingReverse(true);
        gitLogOptions.setOptFileDetails(true);

        // TODO: How come this doesn't work?
        if (repo.lastCommitParsed != null) {
            Logger.info("Only fetching changes after " + repo.lastCommitParsed);
            gitLogOptions.setOptLimitCommitSince(true, repo.lastCommitParsed);
        }

        // TODO: DEBUG
        gitLogOptions.setOptLimitCommitMax(true, 200);

        List<Commit> commitList = null;
        try {
            commitList = dotGit.getLog(gitLogOptions);
        } catch (JavaGitException e) {
            Logger.error(e, "STOP: Cannot synchronize with GITrepository %s.", repo.location);
            return;
        } catch (IOException e) {
            Logger.error(e, "STOP: IOException synchronizing with GIT repo %s.", repo.location);
            return;
        }

        int newCommits = 0;

        for (Commit commit : commitList) {
            try {
                RepoCommit processed = processCommit(commit, repo);
                if (processed == null) {
                    continue;
                }

                newCommits++;
                if (repo.svnRevision == null || (repo.svnRevision < processed.svnRevision)) {
                    repo.svnRevision = processed.svnRevision;
                }

                repo.lastCommitParsed = processed.sha;

                if (repo.lastCommitDate == null || repo.lastCommitDate.before(processed.date)) {
                    repo.lastCommitDate = processed.date;
                }
            } catch (ParseException e) {
                Logger.error(e, "Failed to parse commit %s. Ending sync at this point.", commit.getSha());
                break;
            }
        }

        if (repo.earliestCommitDate == null) {
            RepoCommit firstCommit = RepoCommit.find("order by date").first();
            if (null != firstCommit) {
                repo.earliestCommitDate = firstCommit.date;
            }
        }

        repo.save();

        long endTime = System.currentTimeMillis();
        long jobTime = (endTime - startTime) / 1000;
        Logger.info("STOP Repository sync complete.\nProcessed %d new commits in %d seconds.", newCommits, jobTime);

        // Kick-off the linkage job
        PCALinkageJob linkJob = new PCALinkageJob();
        linkJob.now();
    }

    private RepoCommit processCommit(final Commit commit, final GITRepository repo) throws ParseException {
        Logger.debug("Commit: %s %s %s", commit.getSha(), commit.getDateString(), commit.getAuthor());
        RepoCommit repoCommit = RepoCommit.find("bySha", commit.getSha()).first();
        if (null == repoCommit) {
            repoCommit = new RepoCommit(commit);
            if (!repoCommit.sharedInRemoteRepository()) {
                // Skip commits that are local only and may still change
                return null;
            }

            repoCommit.save();

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
                    }

                    repoFile.linkUpdateNeeded = true;
                    repoFile.save();

                    // Associate the commit
                    RepoFileCommit repoFileCommit = new RepoFileCommit();
                    repoFileCommit.file = repoFile;
                    repoFileCommit.commit = repoCommit;
                    repoFileCommit.linesAdded = commitFile.getLinesAdded();
                    repoFileCommit.linesRemoved = commitFile.getLinesDeleted();
                    repoFileCommit.save();
                }
            }

            return repoCommit;
        }

        return null;
    }
}
