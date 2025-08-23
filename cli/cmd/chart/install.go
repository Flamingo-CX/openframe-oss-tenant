package chart

import (
	"github.com/flamingo/openframe/internal/chart/prerequisites"
	"github.com/flamingo/openframe/internal/chart/services"
	"github.com/flamingo/openframe/internal/chart/utils/types"
	sharedErrors "github.com/flamingo/openframe/internal/shared/errors"
	"github.com/spf13/cobra"
)

// getInstallCmd returns the install subcommand
func getInstallCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "install [cluster-name]",
		Short: "Install ArgoCD and app-of-apps",
		Long: `Install ArgoCD and app-of-apps on a Kubernetes cluster

This command installs:
1. ArgoCD (version 8.1.4) with custom values
2. App-of-apps from GitHub repository (configurable)

The cluster must exist before running this command.
Certificates are automatically regenerated during installation.

Examples:
  openframe chart install                                    # Install with defaults
  openframe chart install my-cluster                        # Install on specific cluster
  openframe chart install --github-branch develop          # Use develop branch
  openframe chart install --cert-dir /path/to/certs        # Custom cert directory
  openframe chart install --github-username myuser --github-token github_pat_xyz123  # Skip credential prompts`,
		PreRunE: func(cmd *cobra.Command, args []string) error {
			return prerequisites.NewInstaller().RegenerateCertificatesOnly()
		},
		RunE: runInstallCommand,
		SilenceErrors: true,  // Errors are handled by our custom error handler
		SilenceUsage: true,   // Don't show usage on errors
	}

	// Add flags directly
	addInstallFlags(cmd)

	return cmd
}

// runInstallCommand handles the install command execution
func runInstallCommand(cmd *cobra.Command, args []string) error {
	// Extract flags directly
	flags, err := extractInstallFlags(cmd)
	if err != nil {
		return err
	}

	// Get verbose flag (with fallback)
	verbose := getVerboseFlag(cmd)

	// Create service and execute
	chartService := services.NewChartService(flags.DryRun, verbose)
	
	req := types.InstallationRequest{
		Args:           args,
		Force:          flags.Force,
		DryRun:         flags.DryRun,
		Verbose:        verbose,
		GitHubRepo:     flags.GitHubRepo,
		GitHubBranch:   flags.GitHubBranch,
		GitHubUsername: flags.GitHubUsername,
		GitHubToken:    flags.GitHubToken,
		CertDir:        flags.CertDir,
	}

	err = chartService.Install(req)
	if err != nil {
		// Use shared error handler for consistent error display
		return sharedErrors.HandleGlobalError(err, verbose)
	}
	return nil
}

// InstallFlags contains all flags needed for chart installation
type InstallFlags struct {
	Force          bool
	DryRun         bool
	GitHubRepo     string
	GitHubBranch   string
	GitHubUsername string
	GitHubToken    string
	CertDir        string
}

// extractInstallFlags extracts install flags from cobra command
func extractInstallFlags(cmd *cobra.Command) (*InstallFlags, error) {
	flags := &InstallFlags{}
	var err error
	
	if flags.Force, err = cmd.Flags().GetBool("force"); err != nil {
		return nil, err
	}
	
	if flags.DryRun, err = cmd.Flags().GetBool("dry-run"); err != nil {
		return nil, err
	}
	
	if flags.GitHubRepo, err = cmd.Flags().GetString("github-repo"); err != nil {
		return nil, err
	}
	
	if flags.GitHubBranch, err = cmd.Flags().GetString("github-branch"); err != nil {
		return nil, err
	}
	
	if flags.GitHubUsername, err = cmd.Flags().GetString("github-username"); err != nil {
		return nil, err
	}
	
	if flags.GitHubToken, err = cmd.Flags().GetString("github-token"); err != nil {
		return nil, err
	}
	
	if flags.CertDir, err = cmd.Flags().GetString("cert-dir"); err != nil {
		return nil, err
	}
	
	return flags, nil
}

// getVerboseFlag extracts verbose flag with fallback
func getVerboseFlag(cmd *cobra.Command) bool {
	// Try root command first
	if cmd.Root() != nil {
		if verbose, err := cmd.Root().PersistentFlags().GetBool("verbose"); err == nil {
			return verbose
		}
	}
	
	// Try current command
	if verbose, err := cmd.Flags().GetBool("verbose"); err == nil {
		return verbose
	}
	
	// Default to false
	return false
}

// addInstallFlags adds all install flags to the command
func addInstallFlags(cmd *cobra.Command) {
	cmd.Flags().BoolP("force", "f", false, "Force installation even if charts already exist")
	cmd.Flags().Bool("dry-run", false, "Show what would be installed without executing")
	cmd.Flags().String("github-repo", "https://github.com/Flamingo-CX/openframe", "GitHub repository URL")
	cmd.Flags().String("github-branch", "main", "GitHub repository branch")
	cmd.Flags().String("github-username", "", "GitHub username (will prompt if not provided)")
	cmd.Flags().String("github-token", "", "GitHub Personal Access Token (will prompt if not provided)")
	cmd.Flags().String("cert-dir", "", "Certificate directory (auto-detected if not provided)")
}

