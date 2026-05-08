{
  description = "silverlining - Android wrapper for SilverBullet";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfreePredicate =
          pkg: builtins.elem (nixpkgs.lib.getName pkg) [ "android-studio" ];
      };
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          go
          nodejs_22
          android-tools
          android-studio
          glib # provides gsettings, which Android Studio probes at startup
        ];
      };
    };
}
