package microservices.examples.tx.course;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMyBatisDAO {
	
	@Insert("INSERT INTO tb_course (id, title, created, updated, description) VALUES(#{id}, #{title}, #{created}, #{updated}, #{description})")
	public void create(Course course);

	@Select("select * from tb_course where id = #{id}")
	public Course select(String id);
	
}
