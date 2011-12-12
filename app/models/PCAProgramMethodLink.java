package models;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.data.validation.Required;

@Entity
public class PCAProgramMethodLink extends PCAProgramClassLink {

    @Required public String methodName;

    @Required public Integer startLine;

    @ManyToOne public PCAProgramClassLink classLink;

    @Override
    public String toString() {
        return super.toString() + "." + methodName;
    }

}
