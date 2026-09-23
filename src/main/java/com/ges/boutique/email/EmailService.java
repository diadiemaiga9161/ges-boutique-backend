package com.ges.boutique.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:maigaamadou@mg-consulting.site}")
    private String from;

    @Value("${app.mail.from-name:MG Boutique}")
    private String fromName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Async
    public void envoyerResetPassword(String destinataire, String nomComplet, String token) {
        try {
            Context ctx = new Context();
            ctx.setVariable("nomComplet", nomComplet);
            ctx.setVariable("lienReset", baseUrl + "/sessions/reset-password?token=" + token);
            ctx.setVariable("baseUrl", baseUrl);

            String contenu = templateEngine.process("email-reset-password", ctx);
            envoyer(destinataire, "Réinitialisation de votre mot de passe", contenu);
            log.info("Mail reset password envoye a {}", destinataire);
        } catch (Exception e) {
            log.error("Erreur envoi mail reset password a {}: {}", destinataire, e.getMessage());
        }
    }

    @Async
    public void envoyerBienvenueVendeur(String destinataire, String nomComplet, String username, String boutiqueName) {
        try {
            Context ctx = new Context();
            ctx.setVariable("nomComplet", nomComplet);
            ctx.setVariable("username", username);
            ctx.setVariable("boutiqueName", boutiqueName);
            ctx.setVariable("lienConnexion", baseUrl);
            ctx.setVariable("baseUrl", baseUrl);

            String contenu = templateEngine.process("email-bienvenue-vendeur", ctx);
            envoyer(destinataire, "Bienvenue sur " + boutiqueName + " — Votre compte est créé", contenu);
            log.info("Mail bienvenue envoye a {} ({})", nomComplet, destinataire);
        } catch (Exception e) {
            log.error("Erreur envoi mail bienvenue a {}: {}", destinataire, e.getMessage());
        }
    }

    // Envoi synchrone (pas @Async) volontaire : appelé depuis la sauvegarde programmée,
    // qui doit savoir si l'envoi a réussi ou échoué (voir BackupServiceImpl) pour logguer
    // correctement, plutôt que d'échouer silencieusement en tâche de fond.
    public void envoyerSauvegarde(String destinataire, String nomBoutique, File fichierSauvegarde) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        try {
            helper.setFrom(from, fromName);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new MessagingException("Adresse d'expédition invalide", e);
        }
        helper.setTo(destinataire);
        helper.setSubject("Sauvegarde automatique — " + nomBoutique);
        helper.setText(
                "Bonjour,<br><br>Voici la dernière sauvegarde automatique de la boutique "
                        + nomBoutique + ", en pièce jointe.<br><br>Ce message est envoyé automatiquement.",
                true);
        helper.addAttachment(fichierSauvegarde.getName(), new FileSystemResource(fichierSauvegarde));
        mailSender.send(message);
    }

    private void envoyer(String destinataire, String sujet, String contenuHtml) throws MessagingException, java.io.UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from, fromName);
        helper.setTo(destinataire);
        helper.setSubject(sujet);
        helper.setText(contenuHtml, true);
        mailSender.send(message);
    }
}
