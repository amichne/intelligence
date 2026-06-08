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

  def self.artifact_target
    if OS.mac?
      Hardware::CPU.arm? ? "macos-arm64" : "macos-x64"
    elsif OS.linux?
      Hardware::CPU.arm? ? "linux-arm64" : "linux-x64"
    else
      odie "Unsupported platform"
    end
  end

  desc "Validate and publish amichne-intelligence marketplace primitives"
  homepage "https://github.com/amichne/intelligence"
  version ARTIFACT_VERSION
  license "Apache-2.0"
  disable! date: "2026-06-08", because: "native release assets have not been published yet"

  livecheck do
    url :stable
    strategy :github_releases
  end

  on_macos do
    on_intel do
      url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}-macos-x64",
          using: :nounzip
      sha256 "0000000000000000000000000000000000000000000000000000000000000000"
    end

    on_arm do
      url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}-macos-arm64",
          using: :nounzip
      sha256 "0000000000000000000000000000000000000000000000000000000000000000"
    end
  end

  on_linux do
    on_intel do
      url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}-linux-x64",
          using: :nounzip
      sha256 "0000000000000000000000000000000000000000000000000000000000000000"
    end

    on_arm do
      url "#{cli_release_root}/#{release_tag}/intelligence-#{release_tag}-linux-arm64",
          using: :nounzip
      sha256 "0000000000000000000000000000000000000000000000000000000000000000"
    end
  end

  def install
    bin.install "intelligence-#{self.class.release_tag}-#{self.class.artifact_target}" => "intelligence"
  end

  test do
    assert_match "Repository CLI", shell_output("#{bin}/intelligence --help")
  end
end
