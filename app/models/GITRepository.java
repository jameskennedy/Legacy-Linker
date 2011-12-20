package models;

import java.util.Date;
import java.util.Properties;

import javax.persistence.Entity;

import play.Play;
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
            Properties playProps = Play.configuration;
            newRepo.location = playProps.getProperty("application.git_repository");
            newRepo.save();
            newRepo.em().flush();
            return newRepo;
        }
    }
}
