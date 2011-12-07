package services;

import models.PCAProgram;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        PCAProgramClassLink.deleteAll();
        PCAProgram.deleteAll();
        RepoCommit.deleteAll();
        RepoFile.deleteAll();
        Logger.info("Wiped all repository data.");
    }

}
