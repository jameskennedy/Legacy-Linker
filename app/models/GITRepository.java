package models;

import javax.persistence.Entity;

import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class GITRepository extends Model {

    public String name;

    @Unique
    public String location;
    public String lastCommitParsed;

    public Integer svnRevision;

    public static synchronized GITRepository getMainRepository() {
        try {
            return (GITRepository) GITRepository.findAll().get(0);
        } catch (IndexOutOfBoundsException e) {
            GITRepository newRepo = new GITRepository();
            newRepo.save();
            return newRepo;
        }
    }
}
