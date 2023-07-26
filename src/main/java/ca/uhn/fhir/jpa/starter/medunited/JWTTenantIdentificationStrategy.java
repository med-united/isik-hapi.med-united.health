package ca.uhn.fhir.jpa.starter.medunited;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.i18n.HapiLocalizer;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.jpa.partition.IPartitionLookupSvc;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.tenant.ITenantIdentificationStrategy;
import ca.uhn.fhir.util.UrlPathTokenizer;
import jakarta.json.Json;
import jakarta.json.JsonObject;

@Component
public class JWTTenantIdentificationStrategy implements ITenantIdentificationStrategy {

	private static Logger log = Logger.getLogger(JWTTenantIdentificationStrategy.class.getName());

	// List of common email provider domains ordered alphabetically
	private List<String> commonEmailProviderDomains = Arrays.asList("1und1.de", "aol.com", "arcor.de", "freenet-group.de", "gmail.com", "gmx.de", "gmx.net", "hotmail.com", "icloud.com", "ionos.com", "live.com", "msn.com", "outlook.com", "t-online.de", "web.de", "yahoo.com");

	@Autowired
	private IPartitionLookupSvc myPartitionLookupSvc;

	@Override
	public void extractTenant(UrlPathTokenizer theUrlPathTokenizer, RequestDetails theRequestDetails) {
		String authorization = theRequestDetails.getHeader("Authorization");
		try {
			if (authorization != null && !"".equals(authorization)) {
				String[] chunks = authorization.substring(7).split("\\.");
				Base64.Decoder decoder = Base64.getUrlDecoder();
				// String header = new String(decoder.decode(chunks[0]));
				String payload = new String(decoder.decode(chunks[1]));
				JsonObject jwt = Json.createReader(new StringReader(payload)).readObject();
				String email = jwt.getString("email");
				String domain = email.split("@")[1];
	
				String partitionName;
				if (commonEmailProviderDomains.contains(domain)) {
					partitionName = email.replace("@", "-").replaceAll("\\.", "-");
				}
				else {
					partitionName = domain.replace('.', '-');
				}

				theRequestDetails.setTenantId(partitionName);
				createPartitionIfNecessary(partitionName);
				return;
			}
		} catch (Exception ex) {
			log.log(Level.WARNING, "Exception during JWT extraction.", ex);
		}
		HapiLocalizer localizer = theRequestDetails.getServer().getFhirContext().getLocalizer();
		throw new InvalidRequestException(
				Msg.code(307) + localizer.getMessage(RestfulServer.class, "rootRequest.multitenant"));

	}

	public void createPartitionIfNecessary(String partitionName) {
		try {
			myPartitionLookupSvc.getPartitionByName(partitionName);
		} catch (ResourceNotFoundException ex) {
			PartitionEntity partitionEntity = new PartitionEntity();
			Integer max = myPartitionLookupSvc.listPartitions().stream().map(pe -> pe.getId()).reduce(Integer::max)
					.orElse(0);
			partitionEntity.setId(max + 1);
			partitionEntity.setName(partitionName);
			myPartitionLookupSvc.createPartition(partitionEntity);
		}
	}

	@Override
	public String massageServerBaseUrl(String theFhirServerBase, RequestDetails theRequestDetails) {

		return null;
	}

}
