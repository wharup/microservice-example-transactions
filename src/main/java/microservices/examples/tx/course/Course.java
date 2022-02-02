package microservices.examples.tx.course;

import java.time.Instant;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import lombok.Data;

@Entity
@Data
@Table(name = "TB_COURSE")
public class Course {
	@Id
	private String id;
	private String title;
	private String description;
	private Instant created;
	private Instant updated;
	@Version
	private long version;
}
