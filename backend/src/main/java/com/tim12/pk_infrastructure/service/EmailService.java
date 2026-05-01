package com.tim12.pk_infrastructure.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    @Value("${app.mail.from}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendActivationEmail(String toEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Activate your account");

            String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color:#f4f4f4; padding:20px;">
                <div style="max-width:600px; margin:auto; background:white; padding:20px; border-radius:10px;">
                    
                    <h2 style="color:#333;">Welcome!</h2>
                    
                    <p>Thanks for registering. Please activate your account by clicking the button below:</p>
                    
                    <div style="text-align:center; margin:30px 0;">
                        <a href="%s" 
                           style="background-color:#4CAF50; color:white; padding:12px 20px; text-decoration:none; border-radius:5px; font-weight:bold;">
                            Activate Account
                        </a>
                    </div>

                    <p style="color:#777;">This link is valid for 24 hours.</p>

                    <hr/>
                    <p style="font-size:12px; color:#aaa;">
                        If the button doesn't work, copy and paste this link into your browser:
                        <br/>
                        <a href="%s">%s</a>
                    </p>

                </div>
            </body>
            </html>
            """.formatted(link, link, link);

            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendMail(String toEmail, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
