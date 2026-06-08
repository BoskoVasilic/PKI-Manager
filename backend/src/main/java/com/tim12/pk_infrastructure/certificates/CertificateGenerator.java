package com.tim12.pk_infrastructure.certificates;

import com.tim12.pk_infrastructure.model.Issuer;
import com.tim12.pk_infrastructure.model.Subject;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

@Component
public class CertificateGenerator {

    public CertificateGenerator() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static X509Certificate generateCertificate(
            Subject subject,
            Issuer issuer,
            Date startDate,
            Date endDate,
            String serialNumber,
            boolean isCA,
            boolean keyCertSign,
            boolean cRLSign,
            boolean digitalSig,
            boolean keyEncipher,
            boolean serverAuth,
            String crlDistributionPointUrl,
            List<String> sanDnsNames
    ) {
        try {
            JcaContentSignerBuilder builder = new JcaContentSignerBuilder("SHA256WithRSAEncryption");
            builder = builder.setProvider("BC");

            ContentSigner contentSigner = builder.build(issuer.getPrivateKey());

            X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                    issuer.getX500Name(),
                    new BigInteger(serialNumber),
                    startDate,
                    endDate,
                    subject.getX500Name(),
                    subject.getPublicKey()
            );

            //ekstenzije

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

            certGen.addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extUtils.createSubjectKeyIdentifier(subject.getPublicKey())
            );

            certGen.addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extUtils.createAuthorityKeyIdentifier(issuer.getPublicKey())
            );

            certGen.addExtension(
                    Extension.basicConstraints,
                    true,
                    new BasicConstraints(isCA)
            );

            int keyUsageBits = 0;
            if (keyCertSign)  keyUsageBits |= KeyUsage.keyCertSign;
            if (cRLSign)      keyUsageBits |= KeyUsage.cRLSign;
            if (digitalSig)   keyUsageBits |= KeyUsage.digitalSignature;
            if (keyEncipher)  keyUsageBits |= KeyUsage.keyEncipherment;

            if (keyUsageBits != 0) {
                certGen.addExtension(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(keyUsageBits)
                );
            }

            if (serverAuth) {
                certGen.addExtension(
                        Extension.extendedKeyUsage,
                        false,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
                );
            }

            if (serverAuth && sanDnsNames != null && !sanDnsNames.isEmpty()) {
                GeneralName[] generalNames = sanDnsNames.stream()
                        .map(name -> {
                            if (name.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                return new GeneralName(GeneralName.iPAddress, name);
                            }
                            return new GeneralName(GeneralName.dNSName, name);
                        })
                        .toArray(GeneralName[]::new);

                GeneralNames subjectAltNames = new GeneralNames(generalNames);
                certGen.addExtension(
                        Extension.subjectAlternativeName,
                        false,
                        subjectAltNames
                );
            }

            if (crlDistributionPointUrl != null && !crlDistributionPointUrl.isBlank()) {
                GeneralName gn = new GeneralName(
                        GeneralName.uniformResourceIdentifier,
                        new DERIA5String(crlDistributionPointUrl)
                );
                GeneralNames gns = new GeneralNames(gn);
                DistributionPointName dpn = new DistributionPointName(gns);
                DistributionPoint dp = new DistributionPoint(dpn, null, null);
                CRLDistPoint crlDistPoint = new CRLDistPoint(new DistributionPoint[]{dp});

                certGen.addExtension(
                        Extension.cRLDistributionPoints,
                        false,
                        crlDistPoint
                );
            }

            X509CertificateHolder certHolder = certGen.build(contentSigner);

            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
            certConverter = certConverter.setProvider("BC");

            return certConverter.getCertificate(certHolder);

        } catch (OperatorCreationException e) {
            throw new RuntimeException("Error creating content signer: " + e.getMessage(), e);
        } catch (CertificateException e) {
            throw new RuntimeException("Error converting certificate: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not found: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error generating certificate: " + e.getMessage(), e);
        }
    }
}