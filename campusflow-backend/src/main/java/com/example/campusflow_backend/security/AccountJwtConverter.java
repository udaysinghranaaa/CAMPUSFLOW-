package com.example.campusflow_backend.security;
import com.example.campusflow_backend.repository.UserAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Component
public class AccountJwtConverter implements Converter<Jwt,AbstractAuthenticationToken> {
    private final UserAccountRepository accounts;
    public AccountJwtConverter(UserAccountRepository accounts) { this.accounts=accounts; }
    @Override @Transactional(readOnly=true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            long id=Long.parseLong(jwt.getSubject());
            var account=accounts.findById(id).orElseThrow(AccountJwtConverter::invalid);
            Object version=jwt.getClaim("ver");
            if(!account.isEnabled() || !(version instanceof Number number)
                    || number.longValue()!=account.getSecurityVersion()
                    || !account.getRole().name().equals(jwt.getClaimAsString("role"))) throw invalid();
            return UsernamePasswordAuthenticationToken.authenticated(
                    new AccountPrincipal(account.getId(),account.getRole(),account.getSecurityVersion()),null,
                    List.of(new SimpleGrantedAuthority("ROLE_"+account.getRole().name())));
        } catch(NumberFormatException|NullPointerException ex) { throw invalid(); }
    }
    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Invalid access token.");
    }
}
