<html>
	<head>
		<title>${project.name}</title>
		<style>
			table {
				border: 0px;
			}
		</style>
	</head>
	<body>
  		<h1>${project.name}</h1>

		<table>
  			<thead>
			  	<tr>
  					<th>Repository</th>
  				</tr>
  			</thead>
  			<tbody>
    			<#list repositories as k,v>
  				<tr>
					<td><a href="${k}">${v.artifactRepositoryFacade.repository.name}</a></td>
				</tr>
    			</#list>
    		</tbody>
  		</table>
	</body>
</html>
