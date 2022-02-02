package microservices.examples.tx.gateway;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import microservices.examples.tx.system.UserDetails;

@Component
public class MemberGateway {

	public String addManager(String id, UserDetails loginUser) {
		return null;
	}

	public void romoveManager(String id, UserDetails loginUser) {
	}

}
