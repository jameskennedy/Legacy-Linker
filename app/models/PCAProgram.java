package models;

import play.*;
import play.db.jpa.*;

import javax.persistence.*;
import java.util.*;

@Entity
public class PCAProgram extends Model {
	
	public String name;
	public String description;
	public String author;
	
	public PCAProgram(String name, String description, String author) {
		this.name = name;
		this.description = description;
		this.author = author;
		
	}
    
}
