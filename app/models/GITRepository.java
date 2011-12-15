package models;

import java.util.Date;

import javax.persistence.Entity;

import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class GITRepository extends Model {

    public String name;

    @Unique
    public String location;
    public String lastCommitParsed;
    public Date lastCommitDate;
    public Date earliestCommitDate;

    public Integer svnRevision;

    public static synchronized GITRepository getMainRepository() {
        try {
            GITRepository mainRepo = (GITRepository) GITRepository.findAll().get(0);
            return mainRepo;
        } catch (IndexOutOfBoundsException e) {
            GITRepository newRepo = new GITRepository();
            newRepo.location = "/triad";
            newRepo.save();
            return newRepo;
        }
    }
}
