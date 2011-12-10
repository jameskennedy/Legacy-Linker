package services;

import java.util.List;

import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        PCAProgramClassLink.deleteAll();
        List<RepoFile> allRepoFiles = RepoFile.findAll();
        for (RepoFile repoFile : allRepoFiles) {
            repoFile.commits.clear();
            repoFile.save();
        }
        RepoFile.deleteAll();
        RepoCommit.deleteAll();
        // PCAProgram.deleteAll();
        Logger.info("Wiped all repository data.");
    }

}
