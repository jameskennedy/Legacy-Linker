package models;

import javax.persistence.Entity;

import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class GITRepository extends Model {

	private static final Long MAIN_REPO_ID = 1L;

	public String name;

	@Unique
	public String location;
	public String lastCommitParsed;

	public static synchronized GITRepository getMainRepository() {
		GITRepository currentRepo = GITRepository.findById(MAIN_REPO_ID);
		if (null == currentRepo) {
			currentRepo = new GITRepository();
			currentRepo.id = MAIN_REPO_ID;
			currentRepo.save();
		}
		return currentRepo;
	}

}
