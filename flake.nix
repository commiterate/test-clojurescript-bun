#
# Nix flake.
#
# https://nix.dev/manual/nix/stable/command-ref/new-cli/nix3-flake.html#flake-format
# https://wiki.nixos.org/wiki/Flakes#Flake_schema
#
{
  description = "Nix flake.";

  inputs = {
    # https://search.nixos.org/packages?channel=unstable
    nixpkgs = {
      type = "github";
      owner = "NixOS";
      repo = "nixpkgs";
      ref = "nixpkgs-unstable";
    };
  };

  outputs =
    { self, nixpkgs }:
    let
      # Systems to provide outputs for.
      systems = [
        "aarch64-darwin"
        "aarch64-linux"
        "x86_64-darwin"
        "x86_64-linux"
      ];

      # A function that creates an attribute set and provides a system-specific nixpkgs for each system.
      forEachSystem =
        f: nixpkgs.lib.genAttrs systems (system: f { pkgs = import nixpkgs { inherit system; }; });
    in
    {
      # Formatter.
      #
      # For `nix fmt`.
      formatter = forEachSystem ({ pkgs }: pkgs.nixfmt-rfc-style);

      # Development shells.
      #
      # For `nix develop` and direnv's `use flake`.
      devShells = forEachSystem (
        { pkgs }:
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              # Nix.
              #
              # Nix is dynamically linked on some systems. If we set LD_LIBRARY_PATH,
              # running Nix commands with the system-installed Nix may fail due to mismatched library versions.
              nix
              # Utilities.
              coreutils
              # Git.
              git
              git-lfs
              # Java.
              jdk21
              # Clojure.
              clojure
              cljfmt
              clj-kondo
              # Bun.
              bun
            ];

            shellHook = ''
              echo "⚗️"
            '';
          };
        }
      );
    };
}
