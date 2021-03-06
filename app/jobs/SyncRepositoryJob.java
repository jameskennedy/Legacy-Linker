package jobs;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.FlushModeType;

import models.GITRepository;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import models.RepoFileCommit;
import play.Logger;
import play.jobs.Every;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.libs.F.Promise;
import services.LegacyLinkageService;
import edu.nyu.cs.javagit.api.DotGit;
import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.commands.GitLogOptions;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.Commit;
import edu.nyu.cs.javagit.api.commands.GitLogResponse.CommitFile;

/**
 * This is the main background job that kicks off all the other jobs. It
 * initially ensures that all legacy programs have been imported with
 * {@link ImportCobolProgramsJob}. It then does the work of pulling in all not
 * yet processed commits from the repository and recording all relevant info to
 * the db. It then kicks off {@link LegacyLinkageJob} to analyse the repository
 * .java source code.
 * 
 * @author james.kennedy
 * 
 */
@OnApplicationStart(async = true)
@Every("10mn")
public class SyncRepositoryJob extends Job {

    private static final int MAX_COMMITS_PER_RUN = 500;
    private static Boolean inProgress = Boolean.FALSE;

    @Override
    public void doJob() {
        synchronized (inProgress) {
            if (inProgress) {
                Logger.debug("SKIP: Skipped syncing with repo since job already in progress.");
                return;
            }
            inProgress = Boolean.TRUE;
        }

        try {

            ImportCobolProgramsJob pImport = new ImportCobolProgramsJob();
            try {
                Promise<Boolean> promise = pImport.now();
                Boolean result = promise.get(60, TimeUnit.SECONDS);
                if (result == null || !result) {
                    Logger.error("STOP: Cannot synchronize with repository without legacy programs loaded.");
                    return;
                }
            } catch (Exception e) {
                Logger.error(e, "STOP: Cannot synchronize with repository.");
                return;
            }

            long startTime = System.currentTimeMillis();

            Logger.info("START: Git repository sync...");

            GITRepository repo = GITRepository.getMainRepository();

            RepoCommit.em().setFlushMode(FlushModeType.COMMIT);

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

            if (repo.lastCommitParsed != null) {
                Logger.info("Only fetching changes after " + repo.lastCommitParsed);
                gitLogOptions.setOptLimitCommitSince(true, repo.lastCommitParsed);
            }

            // TODO: DEBUG
            // gitLogOptions.setOptLimitCommitMax(true, 200);

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

                    if (newCommits % MAX_COMMITS_PER_RUN == 0) {
                        break;
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

            logPerformance(startTime, newCommits);

            Logger.info("STOP Repository sync finished after processing %d commits.", newCommits);

            // Kick-off the linkage job
            LegacyLinkageJob linkJob = new LegacyLinkageJob();

            // FIXME: Why does this commit seem necessary for linkJob to see
            // changes?
            PCAProgramClassLink.em().getTransaction().commit();
            linkJob.now();
        } finally {
            inProgress = Boolean.FALSE;
        }
    }

    private void logPerformance(final long startTime, final int newCommits) {
        if (newCommits > 0) {
            long endTime = System.currentTimeMillis();
            float jobTime = (endTime - startTime) / 1000f;
            Logger.info("Processed %d new commits in %f seconds. Average is %f", newCommits, jobTime, jobTime
                            / newCommits);
        }
    }

    private RepoCommit processCommit(final Commit commit, final GITRepository repo) throws ParseException {
        Logger.debug("Commit: %s %s %s", commit.getSha(), commit.getDateString(), commit.getAuthor());
        RepoCommit repoCommit = RepoCommit.findById(commit.getSha());
        if (null != repoCommit) {
            return null;
        }

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

                RepoFile repoFile = RepoFile.findById(path);
                if (null == repoFile) {
                    repoFile = new RepoFile(repo, path);
                }

                repoFile.linkUpdateNeeded = Boolean.TRUE;
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

        LegacyLinkageService.linkCommitToPrograms(repoCommit);

        return repoCommit;
    }
}
