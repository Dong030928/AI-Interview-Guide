package interview.guide.modules.auth.service;

import interview.guide.modules.auth.model.SysUserEntity;
import interview.guide.modules.auth.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final SysUserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    SysUserEntity user = userRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

    Set<GrantedAuthority> authorities = new HashSet<>();
    user.getRoles().forEach(role -> {
      authorities.add(new SimpleGrantedAuthority(role.getRoleCode()));
      role.getPermissions().forEach(permission ->
          authorities.add(new SimpleGrantedAuthority(permission.getPermissionCode()))
      );
    });

    return new User(
        user.getId().toString(),
        user.getPassword(),
        user.getEnabled(),
        true,
        true,
        true,
        authorities
    );
  }
}
