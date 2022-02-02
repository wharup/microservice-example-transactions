package microservices.examples.tx.system;

import lombok.Data;

@Data
public class UserDetails {
	public String getId() {
		return "";
	}

	public String[] getRoles() {
		return new String[] {"CALL_MANAGER"};
	}

	public String getDepartmentId() {
		return "__DEPT_ID__5c-41dc-968e-bc6b1fd8331e";
	}

}
