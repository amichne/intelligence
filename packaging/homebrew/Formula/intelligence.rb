# frozen_string_literal: true

class Intelligence < Formula
  ARTIFACT_VERSION = "0.0.0"
  DEFAULT_ARTIFACT_ROOT = "https://github.com/amichne"

  def self.artifact_root
    ENV.fetch("HOMEBREW_INTELLIGENCE_ARTIFACT_ROOT", DEFAULT_ARTIFACT_ROOT).chomp("/")
  end

  def self.cli_release_root
    ENV.fetch("HOMEBREW_INTELLIGENCE_CLI_RELEASE_ROOT", "#{artifact_root}/intelligence/releases/download").chomp("/")
  end

  def self.release_tag
    "v#{ARTIFACT_VERSION}"
  end

  desc "Operate portable AI tooling marketplaces"
  homepage "https://github.com/amichne/intelligence"
  url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}.tar.gz"
  version ARTIFACT_VERSION
  sha256 "0000000000000000000000000000000000000000000000000000000000000000"
  license "Apache-2.0"

  livecheck do
    url :stable
    strategy :github_releases
  end

  disable! date: "2026-06-08", because: "JVM release assets have not been published yet"

  depends_on "openjdk@21"

  def install
    libexec.install "bin", "lib"
    (bin/"intelligence").write_env_script libexec/"bin/intelligence", JAVA_HOME: formula_opt_prefix("openjdk@21")
  end

  test do
    assert_match "portable plugin marketplaces", shell_output("#{bin}/intelligence --help")
    assert_match "intelligence version", shell_output("#{bin}/intelligence --version")
  end
end
