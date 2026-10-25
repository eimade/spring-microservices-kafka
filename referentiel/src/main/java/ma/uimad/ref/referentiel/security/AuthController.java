package ma.uimad.ref.referentiel.security;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import ma.uimad.ref.referentiel.web.ReferenceDtos.LoginRequest;
import ma.uimad.ref.referentiel.web.ReferenceDtos.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;

    public AuthController(UserDetailsService users, PasswordEncoder encoder, JwtEncoder jwtEncoder, JwtProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        UserDetails user;
        try {
            user = users.loadUserByUsername(req.username());
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Identifiants invalides");
        }
        if (!encoder.matches(req.password(), user.getPassword())) {
            throw new BadCredentialsException("Identifiants invalides");
        }
        List<String> roles = user.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).sorted().toList();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("uimad-ref").subject(user.getUsername())
                .issuedAt(now).expiresAt(now.plusSeconds(props.ttlSeconds()))
                .claim("roles", roles).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, user.getUsername(), roles, props.ttlSeconds());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
