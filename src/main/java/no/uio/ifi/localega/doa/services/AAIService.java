package no.uio.ifi.localega.doa.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.clearinghouse.Clearinghouse;
import no.uio.ifi.clearinghouse.model.Visa;
import no.uio.ifi.clearinghouse.model.VisaType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for extracting datasets access information from the JWT token.
 */
@Slf4j
@Service
public class AAIService {

    @Value("${ga4gh.passport.openid-configuration-url}")
    private String openIDConfigurationURL;

    @Value("${ga4gh.passport.public-key-path}")
    private String passportPublicKeyPath;

    @Value("${ga4gh.visa.public-key-path}")
    private String visaPublicKeyPath;

    /**
     * Extracts datasets access information from the JWT token.
     *
     * @param jwtToken JWT token with access info in GA4GH Passport format.
     * @return IDs of datasets user has access to.
     */
    public Collection<String> getDatasetIds(String jwtToken) {
        DecodedJWT decodedJWT = JWT.decode(jwtToken);
        boolean isVisa = decodedJWT.getClaims().containsKey("ga4gh_visa_v1");
        Collection<Visa> visas = new ArrayList<>();
        if (isVisa) {
            getVisa(jwtToken).ifPresent(visas::add);
        } else {
            visas.addAll(getVisas(jwtToken));
        }
        List<Visa> controlledAccessGrantsVisas = visas
                .stream()
                .filter(v -> v.getType().equalsIgnoreCase(VisaType.ControlledAccessGrants.name()))
                .collect(Collectors.toList());
        log.info("Authentication and authorization attempt. User {} provided following valid GA4GH Visas: {}", decodedJWT.getSubject(), controlledAccessGrantsVisas);
        Set<String> datasets = controlledAccessGrantsVisas
                .stream()
                .map(Visa::getValue)
                .map(d -> StringUtils.stripEnd(d, "/"))
                .map(d -> StringUtils.substringAfterLast(d, "/"))
                .collect(Collectors.toSet());
        log.info("User has access to the following datasets: {}", datasets);
        return datasets;
    }

    protected Collection<Visa> getVisas(String accessToken) {
        Collection<String> visaTokens;
        try {
            String passportPublicKey = Files.readString(Path.of(passportPublicKeyPath));
            visaTokens = Clearinghouse.INSTANCE.getVisaTokensWithPEMPublicKey(accessToken, passportPublicKey);
        } catch (IOException e) {
            visaTokens = Clearinghouse.INSTANCE.getVisaTokens(accessToken, openIDConfigurationURL);
        }
        return visaTokens
                .stream()
                .map(this::getVisa)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    protected Optional<Visa> getVisa(String visaToken) {
        try {
            String visaPublicKey = Files.readString(Path.of(visaPublicKeyPath));
            return Clearinghouse.INSTANCE.getVisaWithPEMPublicKey(visaToken, visaPublicKey);
        } catch (IOException e) {
            return Clearinghouse.INSTANCE.getVisa(visaToken);
        }
    }


}