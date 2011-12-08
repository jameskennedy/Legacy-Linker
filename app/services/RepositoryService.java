package services;

import java.util.List;

import models.PCAProgram;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        PCAProgramClassLink.deleteAll();
        PCAProgram.deleteAll();
        List<RepoFile> allRepoFiles = RepoFile.findAll();
        for (RepoFile repoFile : allRepoFiles) {
            repoFile.commits.clear();
            repoFile.save();
        }
        RepoFile.deleteAll();
        RepoCommit.deleteAll();
        Logger.info("Wiped all repository data.");
    }

}
