mkdir -p ~/.gradle
cat >> ~/.gradle/gradle.properties <<'EOF'

# Beam Android release signing (not in git)
BEAM_KEYSTORE_PATH=/Users/yjlee/beam-release.jks
BEAM_KEYSTORE_PASSWORD="prota4Neil!@#"
BEAM_KEY_ALIAS=beam
BEAM_KEY_PASSWORD="prota4Neil!@#"
EOF
