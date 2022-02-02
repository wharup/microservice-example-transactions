package microservices.examples.tx.gateway;

import org.springframework.stereotype.Component;

import microservices.examples.tx.system.UserDetails;

@Component
public class BoardGateway {

	public String addBoard(String id, UserDetails loginUser) {
		return null;
	}

	public void removeBoard(String courseId, UserDetails loginUser) {
	}

}
