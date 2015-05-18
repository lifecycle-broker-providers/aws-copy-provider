package org.cloudfoundry.community.servicebroker.datalifecycle.aws;

import static org.cloudfoundry.community.servicebroker.datalifecycle.config.LCCatalogConfig.PRODUCTION;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;
import org.cloudfoundry.community.servicebroker.datalifecycle.provider.CopyProvider;
import org.cloudfoundry.community.servicebroker.exception.ServiceBrokerException;
import org.springframework.beans.factory.annotation.Autowired;

public class AWSCopyProvider implements CopyProvider {

	private Logger log = Logger.getLogger(AWSCopyProvider.class);

	private AWSHelper aws;

	private HashMap<String, Object> creds;

	private final static String description = "CF Service Broker Snapshot Image";

	private HashMap<String, String> instanceImages;

	@Autowired
	public AWSCopyProvider(final AWSHelper aws, String username,
			String password, String uri, String sourceInstance) {
		this.aws = aws;
		creds = new HashMap<>();
		creds.put("username", username);
		creds.put("password", password);
		creds.put("uri", uri);
		instanceImages = new HashMap<>();
		instanceImages.put(sourceInstance, PRODUCTION);
	}

	@Override
	public String createCopy(String instanceId) throws ServiceBrokerException {
		log.info("Creating copy instance " + instanceId);
		String amiId;
		try {
			amiId = aws.createAMI(instanceId, description);
		} catch (TimeoutException e) {
			throw new ServiceBrokerException(e);
		}
		String instance = aws.startEC2Instance(amiId);
		instanceImages.put(instance, amiId);
		return instance;
	}

	@Override
	public void deleteCopy(final String instance) throws ServiceBrokerException {
		log.info("Deleting copy " + instance);
		aws.terminateEc2Instance(instance);
		aws.deregisterAMI(instanceImages.get(instance));
		aws.deleteStorageArtifacts(instanceImages.get(instance));
		instanceImages.remove(instance);
	}

	@Override
	public Map<String, Object> getCreds(final String instance)
			throws ServiceBrokerException {
		if (!instanceImages.containsKey(instance)) {
			return null;
		}
		Map<String, Object> newCreds = new HashMap<>(creds);

		String instanceIp = aws.getEC2InstancePublicIp(instance);
		String pgURI = (String) creds.get("uri");

		try {
			newCreds.put("uri",
					pgURI.replace(new URI(pgURI).getHost(), instanceIp));
		} catch (URISyntaxException e) {
			log.error("Bad URI!!" + pgURI);
			throw new ServiceBrokerException(e);
		}
		return newCreds;
	}
}
