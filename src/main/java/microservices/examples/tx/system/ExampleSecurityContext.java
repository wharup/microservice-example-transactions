package microservices.examples.tx.system;

public class ExampleSecurityContext {

	public static UserDetails getCurrentLoginUser() {
		return new UserDetails();
	}

}
