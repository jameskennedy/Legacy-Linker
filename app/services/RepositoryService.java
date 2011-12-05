package services;

import models.RepoCommit;

public class RepositoryService {

	public static void wipeRepositoryData() {
		RepoCommit.deleteAll();
	}

}
